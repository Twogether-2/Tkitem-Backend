package tkitem.backend.domain.tour.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.KnnSearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
     * 사용자 TEXT를 임베딩하여 TDS 인덱스에 kNN 조회 후, 투어별 상위 m개 점수 평균을 반환
     * @param queryText 사용자 텍스트
     * @param allowTourIds 허용 투어 ID 집합
     * @param k 후보군 수
     * @param candidates
     * @param mTop 투어별 상위 m 개 평균
     * @return 투어 ID -> sEs 점수
     * @throws Exception
     */
    public Map<Long, Double> computeEsScores(
            String queryText,
            List<Long> allowTourIds,
            int k, int candidates, int mTop) throws Exception{

        // 쿼리 임베딩
        float[] qv = embeddingService.embed(queryText); // 텍스트를 1536D 벡터로 변환
        List<Float> qvList = new ArrayList<>(qv.length);
        for(float v : qv) qvList.add(v);

        // KNN 검색(코사인 유사도 검색)
        KnnSearchResponse<Map> resp = esClient.knnSearch(s -> s
                .index(INDEX_TDS)
                .knn(kn -> kn
                        .field("embedding")
                        .queryVector(qvList)
                        .k(k) // 상위 k 개 후보
                        .numCandidates(candidates)) // 내부 탐색폭을 candidates 로 지정
                .source(src -> src.filter(f -> f.includes("tour_id", "title", "schedule_date"))), // 응답 페이로드를 필요한 필드만 포함시킴
                Map.class);

        // 투어별 점수 수집
        Map<Long, List<Double>> byTour = new ConcurrentHashMap<>();
        // 각 히트별 처리
        resp.hits().hits().forEach(hit -> {
            Map<String, Object> src = hit.source();
            if(src == null) return;
            Object tidObj = src.get("tour_id");
            if(tidObj == null) return;
            Long tourId  = Long.valueOf(String.valueOf(tidObj));
            if(allowTourIds != null && !allowTourIds.contains(tourId)) return; // DB 후보군 뽑은것과 교집합 일 경우만 다음step
            byTour.computeIfAbsent(tourId, k2 -> new ArrayList<>()).add(hit.score()); // 해당 투어의 유사도 점수 추가
        });

        //투어별 상위 m개 평균
        Map<Long, Double> sEs = new ConcurrentHashMap<>();
        for (Map.Entry<Long, List<Double>> e : byTour.entrySet()) {
            List<Double> top = e.getValue().stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(mTop, 1))
                    .toList();
            double avg = top.stream().mapToDouble(Double::doubleValue).average().orElse(0.0); // ES kNN 의 코사인 유사도 점수 중 상위 mTop개 평균
            sEs.put(e.getKey(), avg);
        }

        return sEs;
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

            // 2) bool 쿼리 구성 (filter / should / must_not / must)
            java.util.List<Object> filterArr = new java.util.ArrayList<>();
            if (allowTourIds != null && !allowTourIds.isEmpty()) {
                // terms: tour_id IN allowIds (chunking은 ES 요청 크기 이슈 있을 때만 도입)
                java.util.List<Long> ids = new java.util.ArrayList<>(allowTourIds);
                java.util.Map<String, Object> terms = new LinkedHashMap<>();
                terms.put("tour_id", ids);
                filterArr.add(java.util.Map.of("terms", terms));
            }

            java.util.List<Object> shouldArr = new java.util.ArrayList<>();
            if (rule.getShouldList() != null) {
                for (String s : rule.getShouldList()) {
                    if (s == null || s.isBlank()) continue;
                    String term = s; // 원문 유지
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("title",       java.util.Map.of("query", term, "slop", 0, "boost", 4))));
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("description", java.util.Map.of("query", term, "slop", 0, "boost", 3))));
                    shouldArr.add(java.util.Map.of("match_phrase", java.util.Map.of("combined_text", java.util.Map.of("query", term, "slop", 0, "boost", 2))));
                }
            }

            java.util.List<Object> mustNotArr = new java.util.ArrayList<>();
            if (rule.getExcludeList() != null) {
                for (String x : rule.getExcludeList()) {
                    if (x == null || x.isBlank()) continue;
                    String term = x;
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("title",       java.util.Map.of("query", term, "slop", 0))));
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("description", java.util.Map.of("query", term, "slop", 0))));
                    mustNotArr.add(java.util.Map.of("match_phrase", java.util.Map.of("combined_text", java.util.Map.of("query", term, "slop", 0))));
                }
            }

            java.util.Map<String, Object> bool = new LinkedHashMap<>();
            if (!filterArr.isEmpty()) bool.put("filter", filterArr);
            if (!shouldArr.isEmpty()) {
                bool.put("should", shouldArr);
                bool.put("minimum_should_match", 1);
            }
            if (!mustNotArr.isEmpty()) bool.put("must_not", mustNotArr);
            bool.put("must", java.util.Map.of("match_all", java.util.Map.of()));

            // 3) 루트 요청 본문 구성
            java.util.Map<String, Object> root = new LinkedHashMap<>();
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
            root.put("query", java.util.Map.of("bool", bool));
            root.put("knn", java.util.Map.of(
                    "field", "embedding",
                    "query_vector", qvList,
                    "k", Math.max(k, 1),
                    "num_candidates", Math.max(numCandidates, 1),
                    "filter", java.util.Map.of("bool", bool) // 동일 bool을 knn 필터에도 적용
            ));

            // 4) 직렬화
            ObjectMapper om = new ObjectMapper();
            String raw = om.writeValueAsString(root);
            log.info("[ES][V2-AllowedOnly] rawJson={}", raw);
            return raw;

        } catch (Exception e) {
            // 호출부에서 BusinessException 처리하므로 여기서는 던져준다
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
}
