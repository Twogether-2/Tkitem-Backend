package tkitem.backend.domain.tour.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.service.EmbeddingService;
import tkitem.backend.domain.tour.dto.KeywordRule;
import tkitem.backend.domain.tour.dto.TopMatchDto;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourEsService {

    private static final String INDEX_TDS = "tour_detail_schedule_v1";
    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;

    public String searchTop1ByVectorRawJson(KeywordRule rule, Set<Long> allowTourIds) {
        // 1) 임베딩 텍스트(키워드 + shouldList)
        StringBuilder qt = new StringBuilder();
        if (rule.getKeyword() != null && !rule.getKeyword().isBlank()) qt.append(rule.getKeyword()).append(' ');
        if (rule.getShouldList() != null) {
            for (String s : rule.getShouldList()) if (s != null && !s.isBlank()) qt.append(s).append(' ');
        }
        String queryText = qt.toString().trim();

        // 2) 쿼리 벡터(JSON 배열 문자열)
        float[] qv = embeddingService.embed(queryText);
        StringBuilder vec = new StringBuilder("[");
        for (int i = 0; i < qv.length; i++) { if (i > 0) vec.append(','); vec.append(qv[i]); }
        vec.append(']');

        // helpers
        java.util.function.Function<String,String> esc = s -> s == null ? "" : s.replace("\"","\\\"");
        java.util.function.BiFunction<String,String,String> matchPhrase = (field, term) ->
                "{\"match_phrase\":{\"" + field + "\":{\"query\":\"" + term + "\",\"slop\":0}}}";
        java.util.function.Function<String,String> matchPhraseBoostedTitle  = term ->
                "{\"match_phrase\":{\"title\":{\"query\":\"" + term + "\",\"slop\":0,\"boost\":4}}}";
        java.util.function.Function<String,String> matchPhraseBoostedDesc   = term ->
                "{\"match_phrase\":{\"description\":{\"query\":\"" + term + "\",\"slop\":0,\"boost\":3}}}";
        java.util.function.Function<String,String> matchPhraseBoostedCombo  = term ->
                "{\"match_phrase\":{\"combined_text\":{\"query\":\"" + term + "\",\"slop\":0,\"boost\":2}}}";

        // 3) allowTourIds → terms
        StringBuilder allowTerms = new StringBuilder();
        if (allowTourIds != null && !allowTourIds.isEmpty()) {
            boolean first = true;
            for (Long id : allowTourIds) {
                if (id == null) continue;
                if (!first) allowTerms.append(',');
                allowTerms.append(id);
                first = false;
            }
        }
        boolean hasAllow = allowTerms.length() > 0;

        // 4) shouldList → match_phrase(정확 구문 일치, decompound 회피 목적), 최소 1개 조건 충족
        List<String> shouldItems = new ArrayList<>();
        if (rule.getShouldList() != null) {
            for (String s : rule.getShouldList()) {
                if (s == null || s.isBlank()) continue;
                String v = esc.apply(s);
                // title/description/combined_text 각각 아이템 1개씩 (콤마/괄호 정상)
                shouldItems.add(matchPhraseBoostedTitle.apply(v));
                shouldItems.add(matchPhraseBoostedDesc.apply(v));
                shouldItems.add(matchPhraseBoostedCombo.apply(v));
            }
        }
        String shouldJson = String.join(",", shouldItems);
        boolean hasShould = !shouldItems.isEmpty();

        // 5) excludeList → must_not (match_phrase 위주로 간단 배제)
        List<String> mustNotItems = new ArrayList<>();
        if (rule.getExcludeList() != null) {
            for (String x : rule.getExcludeList()) {
                if (x == null || x.isBlank()) continue;
                String v = esc.apply(x);
                mustNotItems.add(matchPhrase.apply("title", v));
                mustNotItems.add(matchPhrase.apply("description", v));
                mustNotItems.add(matchPhrase.apply("combined_text", v));
            }
        }
        String mustNotJson = String.join(",", mustNotItems);
        boolean hasMustNot = !mustNotItems.isEmpty();

        // 6) 공용 bool(JSON) — query.bool 과 knn.filter.bool 둘 다 같은 조건(가독성/안정성)
        List<String> boolParts = new ArrayList<>();
        if (hasAllow) {
            boolParts.add("\"filter\":[{\"terms\":{\"tour_id\":[" + allowTerms + "]}}]");
        }
        if (hasShould) {
            boolParts.add("\"should\":[" + shouldJson + "]");
            boolParts.add("\"minimum_should_match\":1");
        }
        if (hasMustNot) {
            boolParts.add("\"must_not\":[" + mustNotJson + "]");
        }
        String boolJson = "{" + String.join(",", boolParts) + "}";

        // 7) 최종 JSON — 단순: kNN 점수로 랭킹, query.bool은 동일 조건으로 필터 역할
        String json = """
    {
      "size": 1,
      "track_total_hits": false,
      "_source": ["tour_id"],
      "collapse": { "field": "tour_id" },
      "query": { "bool": %s },
      "knn": {
        "field": "embedding",
        "query_vector": %s,
        "k": 50,
        "num_candidates": 200,
        "filter": { "bool": %s }
      }
    }
    """.formatted(boolJson, vec.toString(), boolJson);

        log.info("생성된 json : {}", json);
        return json;
    }

    public TopMatchDto sendRawEsQuery(String rawJson) {
        try {
            RestClient rest = ((RestClientTransport) esClient._transport()).restClient();
            Request req = new Request("POST", "/" + INDEX_TDS + "/_search");
            req.setJsonEntity(rawJson);

            Response resp = rest.performRequest(req);
            String body = new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);

            JsonNode root = new ObjectMapper().readTree(body);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.size() == 0) {
                throw new BusinessException("ES no hit", ErrorCode.ENTITY_NOT_FOUND);
            }

            JsonNode h0 = hits.get(0);
            long tourId = h0.path("_source").path("tour_id").asLong();
            double score = h0.path("_score").asDouble(0.0);
            return new TopMatchDto(tourId, score);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("ES raw 전송 실패: " + e.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }



    /**
     * DB에서 전달한 allowTourIds만으로 ES를 선필터링하고,
     * 부스팅(should) / 제외(must_not) 문장을 그대로 반영하여
     * /{index}/_search + "knn" + collapse(tour_id) (+ inner_hits=size=mTop) RAW JSON을 생성.
     * @param rule
     * @param allowTourIds
     * @param topN 투어 단위로 상위 N개를 받고 싶을 때 사용
     * @param k
     * @param numCandidates
     * @param mTop 투어별 일정 문서 점수 상위 m개를 inner_hits로 내려 받아 평균 계산용(파서 메서드에서 사용)
     * @return
     */
    public String searchTopNByVectorRawJsonAllowedOnly(
            KeywordRule rule,
            List<Long> allowTourIds,
            int topN,
            int k,
            int numCandidates,
            int mTop
    ){
        try {
            // 1) 임베딩 벡터
            StringBuilder qt = new StringBuilder();
            if (rule.getKeyword() != null && !rule.getKeyword().isBlank()) qt.append(rule.getKeyword()).append(' ');
            if (rule.getShouldList() != null) {
                for (String s : rule.getShouldList()) if (s != null && !s.isBlank()) qt.append(s).append(' ');
            }
            String queryText = qt.toString().trim();
            float[] qv = embeddingService.embed(queryText);
            java.util.List<Float> qvList = new java.util.ArrayList<>(qv.length);
            for (float f : qv) qvList.add(f);

            // 2) 공통 filter(allow terms)
            java.util.List<Object> filterArr = new java.util.ArrayList<>();
            if (allowTourIds != null && !allowTourIds.isEmpty()) {
                java.util.List<Long> ids = new java.util.ArrayList<>(allowTourIds);
                java.util.Map<String, Object> terms = new java.util.LinkedHashMap<>();
                terms.put("tour_id", ids);
                filterArr.add(java.util.Map.of("terms", terms));
            }

            // 3) should(부스팅) / must_not(제외)
            java.util.List<Object> shouldArr = new java.util.ArrayList<>();
            if (rule.getShouldList() != null) {
                for (String s : rule.getShouldList()) {
                    if (s == null || s.isBlank()) continue;
                    String term = s;
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("title",         java.util.Map.of("query", term, "slop", 0, "boost", 4))));
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("description",   java.util.Map.of("query", term, "slop", 0, "boost", 3))));
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("combined_text", java.util.Map.of("query", term, "slop", 0, "boost", 2))));
                }
            }

            java.util.List<Object> mustNotArr = new java.util.ArrayList<>();
            if (rule.getExcludeList() != null) {
                for (String x : rule.getExcludeList()) {
                    if (x == null || x.isBlank()) continue;
                    String term = x;
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("title",         java.util.Map.of("query", term, "slop", 0))));
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("description",   java.util.Map.of("query", term, "slop", 0))));
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("combined_text", java.util.Map.of("query", term, "slop", 0))));
                }
            }

            // 4) query.bool (부스팅 전용: minimum_should_match 제거!)
            java.util.Map<String, Object> boolQuery = new java.util.LinkedHashMap<>();
            if (!filterArr.isEmpty())   boolQuery.put("filter",   filterArr);
            if (!mustNotArr.isEmpty())  boolQuery.put("must_not", mustNotArr);
            if (!shouldArr.isEmpty())   boolQuery.put("should",   shouldArr);
            // 필요시 점수 없는 기본 must를 줄 수도 있지만 없어도 유효한 bool

            // 5) knn.filter.bool (가볍게: allow (+ optional must_not)만)
            java.util.Map<String, Object> boolKnn = new java.util.LinkedHashMap<>();
            if (!filterArr.isEmpty())   boolKnn.put("filter",   filterArr);
            if (!mustNotArr.isEmpty())  boolKnn.put("must_not", mustNotArr);

            // 6) 루트 요청
            java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("size", Math.max(topN, 1));
            root.put("track_total_hits", false);
            root.put("_source", java.util.List.of("tour_id"));
            root.put("collapse", java.util.Map.of(
                    "field", "tour_id",
                    "inner_hits", java.util.Map.of(
                            "name", "top_m",
                            "size", Math.max(mTop, 1),
                            "_source", java.util.List.of("tour_id", "title")
                    )
            ));
            root.put("query", java.util.Map.of("bool", boolQuery));
            root.put("knn", java.util.Map.of(
                    "field", "embedding",
                    "query_vector", qvList,
                    "k", Math.max(k, 1),
                    "num_candidates", Math.max(numCandidates, 1),
                    "filter", java.util.Map.of("bool", boolKnn)
            ));

            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            String raw = om.writeValueAsString(root);
            log.info("[ES][V2-AllowedOnly] rawJson={}", raw);
            return raw;

        } catch (Exception e) {
            throw new RuntimeException("Failed to build ES raw json: " + e.getMessage(), e);
        }
    }

    /**
     * RAW JSON을 바로 호출해, collapse된 각 투어에 대해 inner_hits 상위 m개 점수의 평균을 계산하여
     * List<TopMatchDto> (tourId, avgScore)로 반환.
     * 반환 전, avgScore 내림차순으로 정렬합니다.
     * @param rule
     * @param allowTourIds
     * @param topN
     * @param k
     * @param numCandidates
     * @param mTop
     * @return
     */
    public List<TopMatchDto> sendRawEsQueryTopNAllowed(
            KeywordRule rule,
            List<Long> allowTourIds,
            int topN,
            int k,
            int numCandidates,
            int mTop
    ) {
        try {
            String rawJson = searchTopNByVectorRawJsonAllowedOnly(rule, allowTourIds, topN, k, numCandidates, mTop);
            RestClient rest = ((RestClientTransport) esClient._transport()).restClient();
            Request req = new Request("POST", "/" + INDEX_TDS + "/_search");
            req.setJsonEntity(rawJson);

            Response resp = rest.performRequest(req);
            String body = new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);
            JsonNode hits = root.get("hits").path("hits");
            if (!hits.isArray() || hits.size() == 0){
                throw new BusinessException("ES no hit", ErrorCode.ENTITY_NOT_FOUND);
            }

            List<TopMatchDto> out = new ArrayList<>();
            for(JsonNode h :hits){
                long tourId = h.path("_source").path("tour_id").asLong();

                // inner_hits.top_m.hits.hits. 의 _source 평균(mTop 상한 적용)
                JsonNode innerHits = h.path("inner_hits").path("top_m").path("hits").path("hits");
                List<Double> scores = new ArrayList<>();
                if(innerHits.isArray()){
                    for(JsonNode ih : innerHits){
                        scores.add(ih.path("_score").asDouble(0.0));
                    }
                }
                scores.sort(Comparator.reverseOrder());
                double avg = scores.stream()
                        .limit(Math.max(mTop, 1)) // 최소 1개 이상은 평균에 포함되게. mTop 이 0으로 들어오는 실수 방지.
                        .mapToDouble(Double:: doubleValue)
                        .average().orElse(h.path("_score").asDouble(0.0));

                out.add(new TopMatchDto(tourId, avg));
            }

            // 평균점수 내림차순 정렬
            out.sort((a, b) -> Double.compare(b.score(), a.score()));
            return out;
        } catch (BusinessException be){
            throw be;
        } catch (Exception e) {
            throw new BusinessException("ES V2 AllowedOnly 전송 실패: " + e.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // float[] -> JSON 배열 문자열
    private final String toJsonArray(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for(int i=0; i<arr.length; i++){
            if(i>0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }

    // terms JSON 생성. terms 란 Where tour_id IN(...) 과 같은 필터 역할
    private static String termJson(String field, List<Long> values){
        String joined = values.stream().map(Object::toString).collect(Collectors.joining(","));
        return "{\"terms\":{\"" + field + "\":[" + joined + "]}}";
    }

    /// ////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    private final TourLLmService tourLLmService;

    private static final int   SIZE_GROUPED       = 100;     // 투어(그룹) 최대 반환 수
    private static final int   INNER_HITS_MTOP    = 3;       // 투어별 상위 일정 m개
    private static final int   KNN_K              = 50;      // [간소화] kNN K
    private static final int   KNN_CANDIDATES     = 200;     // [간소화] kNN 후보군
    private static final double W_BM25_DEFAULT    = 0.7;     // 기본 BM25 가중
    private static final double W_VEC_DEFAULT     = 0.3;     // 기본 벡터 가중
    private static final double W_BM25_WHEN_WEAK  = 0.4;     // BM25 약할 때
    private static final double W_VEC_WHEN_WEAK   = 0.6;

    // [신규] BM25 “부족 판단” 기준(명확히 제시)
    //  - 총 히트 < 2 이거나
    //  - max_score < 8.0 이거나
    //  - allowTourIds가 아주 큰데(예: 1000+) 커버된 투어 수가 1% 미만
    private static final int    BM25_MIN_HITS       = 2;
    private static final double BM25_MIN_MAX_SCORE  = 8.0;
    private static final double BM25_MIN_COVER_RATE = 0.01;

    // ============================
    // [신규] 외부에 제공할 단일 엔트리 (한 번에 BM25→kNN→융합)
    // ============================
    public HybridResult searchHybridSimple(
            String userText,
            Set<Long> allowTourIds,
            int topN
    ) throws Exception {

        // 0) LLM 보완 (있으면) : should / mustNot 키워드 추출
        KeywordRule rule = null;
//        if (userText != null && !userText.isBlank()) {
//            try {
//                rule = tourLLmService.buildRuleFromQueryText(userText);
//            } catch (Exception e) {
//                log.warn("[ES][LLM] 보완 키워드 추출 실패(무시) : {}", e.getMessage());
//            }
//        }

        // 1) BM25 원문 쿼리 JSON 구성
        String boolFilterJson = buildBoolFilterJson(allowTourIds, rule);       // 허용 투어 + 제외어
        String bm25BodyJson   = buildBm25BodyJson(userText, rule, boolFilterJson);
        log.debug("[ES][BM25] request json=\n{}", bm25BodyJson);               // 원문 노출

        // 2) BM25 실행
        SearchResponse<Map> bm25Resp = esClient.search(
                s -> s.index(INDEX_TDS).withJson(new StringReader(bm25BodyJson)),
                Map.class
        );
        Bm25ParseResult bm25 = parseGroupedScores(bm25Resp);

        // 3) BM25 충분성 판단
        boolean bm25Weak = isBm25Weak(bm25, allowTourIds.size());
        log.info("[ES][BM25] totalHits={}, maxScore={}, distinctTours={}, bm25Weak={}",
                bm25.totalHits, String.format(Locale.ROOT, "%.3f", bm25.maxScore),
                bm25.tourScores.size(), bm25Weak
        );

        Map<Long, Double> finalScores;
        String knnBodyJson;
        boolean usedVector = false;

        if (bm25Weak) {
            // 4) (조건부) kNN 원문 쿼리 JSON 구성
            float[] vec = embeddingService.embed(buildVectorText(userText, rule)); // [신규] 간단히 보완어 포함
            String vecJson = toJsonArray(vec);
            knnBodyJson = buildKnnBodyJson(vecJson, boolFilterJson);              // [신규]
            log.debug("[ES][KNN] request json=\n{}", knnBodyJson);                // [신규] 원문 노출

            // 5) kNN 실행
            SearchResponse<Map> knnResp = esClient.search(
                    s -> s.index(INDEX_TDS).withJson(new StringReader(knnBodyJson)),
                    Map.class
            );
            Bm25ParseResult knn = parseGroupedScores(knnResp); // 파싱 로직 재사용(점수 필드를 동일 취급)
            usedVector = true;

            // 6) 점수 정규화 & 융합
            Map<Long, Double> bm25N = normalizeByMax(bm25.tourScores);
            Map<Long, Double> knnN  = normalizeByMax(knn.tourScores);

            double wB = W_BM25_WHEN_WEAK;
            double wV = W_VEC_WHEN_WEAK;

            finalScores = fuseScores(bm25N, knnN, wB, wV);
        } else {
            knnBodyJson = null;
            // BM25만 사용
            finalScores = bm25.tourScores;
        }

        // 7) 최종 Top-N
        List<Map.Entry<Long, Double>> sorted = finalScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .toList();

        LinkedHashMap<Long, Double> topNMap = new LinkedHashMap<>();
        for (var e : sorted) topNMap.put(e.getKey(), e.getValue());

        // 8) 결과 패키징 (요청하신 “원문 쿼리” 포함)
        HybridResult out = new HybridResult();
        out.bm25QueryJson = bm25BodyJson;
        out.knnQueryJson  = knnBodyJson; // 벡터 미사용이면 null
        out.usedVector    = usedVector;
        out.bm25Hits      = bm25.totalHits;
        out.bm25MaxScore  = bm25.maxScore;
        out.scores        = topNMap;
        return out;
    }

    // ============================
    // [신규] 판단 규칙 (BM25가 부족한지)
    // ============================
    private boolean isBm25Weak(Bm25ParseResult bm25, int allowSize) {
        if (bm25.totalHits < BM25_MIN_HITS) return true;
        if (bm25.maxScore  < BM25_MIN_MAX_SCORE) return true;
        if (allowSize >= 1000) {
            double coverRate = (bm25.tourScores.size() * 1.0) / allowSize;
            if (coverRate < BM25_MIN_COVER_RATE) return true;
        }
        return false;
    }

    // ============================
    // [신규] JSON 원문 빌더
    // ============================

    /** [신규] 허용 투어 + LLM excludeList를 must_not로 반영한 bool JSON */
    private String buildBoolFilterJson(Set<Long> allowTourIds, KeywordRule rule) {
        String terms = allowTourIds == null || allowTourIds.isEmpty()
                ? "\"terms\": {\"tour_id\": []}"
                : "\"terms\": {\"tour_id\": [" + allowTourIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]}";

        // must_not (excludeList → combined_text에 대한 match)
        String mustNot = "";
        if (rule != null && rule.getExcludeList() != null && !rule.getExcludeList().isEmpty()) {
            String blocks = rule.getExcludeList().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> "{\"match\":{\"combined_text\":{\"query\":\"" + jsonEscape(s) + "\",\"operator\":\"or\"}}}")
                    .collect(Collectors.joining(","));
            if (!blocks.isBlank()) {
                mustNot = ",\"must_not\":[" + blocks + "]";
            }
        }

        return "{\"filter\":[" + "{" + terms + "}" + "]" + mustNot + "}";
    }

    /** [신규] BM25 원문 바디(JSON) */
    private String buildBm25BodyJson(String userText, KeywordRule rule, String boolJson) {
        StringBuilder queryText = new StringBuilder();
        if (userText != null) queryText.append(userText).append(' ');
        if (rule != null && rule.getKeyword() != null) queryText.append(rule.getKeyword()).append(' ');
        if (rule != null && rule.getShouldList() != null) {
            rule.getShouldList().stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> queryText.append(s).append(' '));
        }
        String q = jsonEscape(queryText.toString().trim());

        // collapse + inner_hits로 투어 단위 1묶음 반환
        return """
        {
          "track_total_hits": false,
          "_source": { "excludes": ["embedding","embedding.*"] },
          "fields": ["tour_id"],
          "size": %d,
          "collapse": {
            "field": "tour_id",
            "inner_hits": { "name": "top_schedules", "size": %d, "sort": [ { "_score": "desc" } ] }
          },
          "query": {
            "bool": %s
          }
        }
        """.formatted(
                SIZE_GROUPED,
                INNER_HITS_MTOP,
                // bool 내부의 must는 여기서 넣는다
                injectMustIntoBool(boolJson, """
                  {"match":{"combined_text":{"query":"%s","operator":"and"}}}
                """.formatted(q))
        );
    }

    /** [신규] kNN 원문 바디(JSON) */
    private String buildKnnBodyJson(String vectorJson, String boolJson) {
        // collapse + inner_hits 동일 사용
        return """
        {
          "track_total_hits": false,
          "_source": { "excludes": ["embedding","embedding.*"] },
          "fields": ["tour_id"],
          "size": %d,
          "collapse": {
            "field": "tour_id",
            "inner_hits": { "name": "top_schedules", "size": %d, "sort": [ { "_score": "desc" } ] }
          },
          "query": { "bool": %s },
          "knn": {
            "field": "embedding",
            "query_vector": %s,
            "k": %d,
            "num_candidates": %d,
            "filter": { "bool": %s }
          }
        }
        """.formatted(
                SIZE_GROUPED,
                INNER_HITS_MTOP,
                boolJson,
                vectorJson,
                KNN_K,
                KNN_CANDIDATES,
                boolJson
        );
    }

    // ============================
    // [신규] 파싱/가공 유틸
    // ============================

    /** [신규] collapse(+inner_hits) 응답을 투어 단위 점수 맵으로 파싱 */
    private Bm25ParseResult parseGroupedScores(SearchResponse<Map> resp) {
        Map<Long, Double> tourScore = new LinkedHashMap<>();
        double maxScore = 0.0;
        for (Hit<Map> h : resp.hits().hits()) {
            Long tourId = readTourId(h);
            if (tourId == null) continue;

            // 대표 문서 _score + inner_hits 평균 중 더 의미있는 값 사용 (여기선 inner_hits 평균 우선)
            double avg = averageInnerHitsScore(h, "top_schedules");
            if (Double.isNaN(avg)) {
                avg = h.score() == null ? 0.0 : h.score();
            }
            tourScore.put(tourId, avg);
            if (avg > maxScore) maxScore = avg;
        }
        int total = resp.hits().total() == null ? tourScore.size() : (int) resp.hits().total().value();
        Bm25ParseResult r = new Bm25ParseResult();
        r.tourScores = tourScore;
        r.maxScore   = maxScore;
        r.totalHits  = total;
        return r;
    }

    private Long readTourId(Hit<Map> h) {
        Map<String, Object> src = h.source();
        if (src != null && src.get("tour_id") != null) {
            Object v = src.get("tour_id");
            if (v instanceof Number n) return n.longValue();
            try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignore) {}
        }
        // fields에 있을 수도 있음
        if (h.fields() != null && h.fields().get("tour_id") instanceof List<?> list && !list.isEmpty()) {
            Object v = list.get(0);
            if (v instanceof Number n) return n.longValue();
            try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignore) {}
        }
        return null;
    }

    private double averageInnerHitsScore(Hit<Map> h, String innerName) {
        try {
            var ih = h.innerHits();
            if (ih == null || !ih.containsKey(innerName)) return Double.NaN;
            var hits = ih.get(innerName).hits().hits();
            if (hits == null || hits.isEmpty()) return Double.NaN;
            double sum = 0.0; int n = 0;
            for (var x : hits) {
                if (x.score() != null) { sum += x.score(); n++; }
            }
            return n == 0 ? Double.NaN : (sum / n);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private Map<Long, Double> normalizeByMax(Map<Long, Double> in) {
        double max = in.values().stream().mapToDouble(d -> d).max().orElse(0.0);
        if (max <= 0.0) return in;
        Map<Long, Double> out = new HashMap<>(in.size());
        for (var e : in.entrySet()) out.put(e.getKey(), e.getValue() / max);
        return out;
    }

    private Map<Long, Double> fuseScores(Map<Long, Double> bm25, Map<Long, Double> vec, double wB, double wV) {
        Map<Long, Double> out = new HashMap<>();
        Set<Long> keys = new HashSet<>(); keys.addAll(bm25.keySet()); keys.addAll(vec.keySet());
        for (Long id : keys) {
            double s = wB * bm25.getOrDefault(id, 0.0) + wV * vec.getOrDefault(id, 0.0);
            out.put(id, s);
        }
        return out;
    }

    // ============================
    // [신규] 문자열/JSON 도우미
    // ============================
    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").trim();
    }

    /** bool JSON 문자열 안에 must 절 하나를 주입 (기존 must_not/filter 보존) */
    private static String injectMustIntoBool(String boolJson, String mustClauseJson) {
        // boolJson = {"filter":[...], "must_not":[...]} 형태 가정. must 배열이 없으면 생성.
        String trimmed = boolJson.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return boolJson; // 방어
        boolean hasMust = trimmed.contains("\"must\":");
        if (hasMust) {
            return trimmed.replaceFirst("\\\"must\\\"\\s*:\\s*\\[", "\"must\":[" + mustClauseJson + ",");
        } else {
            // 마지막 } 앞에 ,"must":[ ... ] 삽입
            String insert = ",\"must\":[" + mustClauseJson + "]";
            return trimmed.substring(0, trimmed.length() - 1) + insert + "}";
        }
    }

    private String buildVectorText(String userText, KeywordRule rule) {
        StringBuilder sb = new StringBuilder();
        if (userText != null) sb.append(userText).append(' ');
        if (rule != null && rule.getShouldList() != null) {
            rule.getShouldList().stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> sb.append(s).append(' '));
        }
        return sb.toString().trim();
    }

    // ============================
    // [신규] 반환 DTO
    // ============================
    public static class HybridResult {
        public Map<Long, Double> scores;    // 최종 Top-N 점수(투어 단위)
        public String bm25QueryJson;        // BM25 요청 원문 JSON
        public String knnQueryJson;         // kNN 요청 원문 JSON(미사용이면 null)
        public boolean usedVector;          // 벡터 사용 여부
        public int bm25Hits;                // BM25 totalHits (그룹 기준 추정)
        public double bm25MaxScore;         // BM25 max_score
    }

    private static class Bm25ParseResult {
        Map<Long, Double> tourScores;
        int totalHits;
        double maxScore;
    }
}