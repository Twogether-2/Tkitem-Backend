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

//        log.info("생성된 json : {}", json);
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


    /// ////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////
    /// ////////////////////////////////////////////

    // ---- 튜닝 상수(간결 명시) ----
    private static final int SIZE_GROUPED   = 100;   // 투어(그룹) 최대 반환
    private static final int KNN_K          = 50;    // kNN k
    private static final int KNN_CANDIDATES = 200;   // kNN 후보 수

    // [수정] BM25 부족 판단: "결과 0건" 또는 "max_score < 8.0"
    private static final double BM25_MIN_MAX_SCORE = 8.0; // 기준 점수

    // 가중치(부족 시 kNN 가중↑)
    private static final double W_BM25 = 0.7, W_VEC = 0.3;
    private static final double W_BM25_WEAK = 0.4, W_VEC_WEAK = 0.6;

    /* =========================================================
     * 하이브리드 검색:
     *   1) BM25 요청(AND 매치, collapse)
     *   2) [부족이면] kNN 요청
     *   3) 점수 정규화/가중합(부족 시만) → TopN 맵 반환
     * ========================================================= */
    public HybridResult searchHybridSimple(
            String userText,
            Set<Long> allowTourIds,
            int topN,
            KeywordRule rule
    ) throws Exception {
        if (allowTourIds == null || allowTourIds.isEmpty()) {
            return HybridResult.empty();
        }

        // 1) BM25 원문 JSON 구성
        String bm25Json = buildBm25Body(userText, rule, allowTourIds);
        log.debug("[ES][BM25] request=\n{}", bm25Json);

        // 2) BM25 실행
        SearchResponse<Map> bm25Resp = esClient.search(
                s -> s.index(INDEX_TDS).withJson(new StringReader(bm25Json)),
                Map.class
        );
        ParseResult bm25 = parseScores(bm25Resp); // 투어단위 점수/최대점/총건수

        // 3) 부족 판단: 결과 0건 또는 max_score < 8.0
        boolean weak = (bm25.totalHits <= 0) || (bm25.maxScore < BM25_MIN_MAX_SCORE);

        Map<Long, Double> finalScores;
        String knnJson;
        boolean usedVector = false;

        if (weak) {
            // 4) kNN 원문 JSON 구성 (임베딩 텍스트: userText + should)
            String vecText = buildVectorText(userText, rule);
            float[] vec = embeddingService.embed(vecText);
            knnJson = buildKnnBody(vec, rule, allowTourIds);
            log.debug("[ES][kNN] request=\n{}", knnJson);

            // 5) kNN 실행
            SearchResponse<Map> knnResp = esClient.search(
                    s -> s.index(INDEX_TDS).withJson(new StringReader(knnJson)),
                    Map.class
            );
            ParseResult knn = parseScores(knnResp);
            usedVector = true;

            // 6) 정규화 후 가중합 (부족 시만: 0.4*BM25 + 0.6*Vector)
            Map<Long, Double> bN = normalize(bm25.tourScores);
            Map<Long, Double> vN = normalize(knn.tourScores);
            finalScores = fuse(bN, vN, W_BM25_WEAK, W_VEC_WEAK);
        } else {
            knnJson = null;
            // BM25만 사용
            finalScores = bm25.tourScores;
        }

        // 7) Top-N
        LinkedHashMap<Long, Double> top = finalScores.entrySet().stream()
                .sorted((a,b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .collect(LinkedHashMap::new, (m,e)->m.put(e.getKey(), e.getValue()), Map::putAll);

        // 8) 결과 패키징(원문 포함)
        HybridResult out = new HybridResult();
        out.scores        = top;
        out.bm25QueryJson = bm25Json;
        out.knnQueryJson  = knnJson;  // 사용 안 했으면 null
        out.usedVector    = usedVector;
        out.bm25Hits      = bm25.totalHits;
        out.bm25MaxScore  = bm25.maxScore;
        return out;
    }

    /* ---------- JSON 빌더(간결) ---------- */

    // _source를 배열이 아닌 오브젝트 형태로 생성 (Java 클라이언트 역직렬화 오류 방지)
    private String buildBm25Body(String userText, KeywordRule rule, Set<Long> allowIds) {
        String q = buildBm25QueryText(userText, rule);
        String terms = allowIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String mustNot = buildMustNot(rule);

        return """
    {
      "track_total_hits": false,
      "_source": { "includes": ["tour_id"] },
      "size": %d,
      "collapse": { "field": "tour_id" },
      "query": {
        "bool": {
          "filter": [ { "terms": { "tour_id": [ %s ] } } ]%s,
          "must": { "match": { "combined_text": { "query": "%s", "operator": "and" } } }
        }
      }
    }
    """.formatted(SIZE_GROUPED, terms, mustNot, escape(q));
    }

    // _source를 배열이 아닌 오브젝트 형태로 생성 (Java 클라이언트 역직렬화 오류 방지)
    private String buildKnnBody(float[] vec, KeywordRule rule, Set<Long> allowIds) {
        String terms = allowIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String mustNot = buildMustNot(rule);
        String vecJson = toJsonArray(vec);

        return """
    {
      "track_total_hits": false,
      "_source": { "includes": ["tour_id"] },
      "size": %d,
      "collapse": { "field": "tour_id" },
      "query": {
        "bool": {
          "filter": [ { "terms": { "tour_id": [ %s ] } } ]%s
        }
      },
      "knn": {
        "field": "embedding",
        "query_vector": %s,
        "k": %d,
        "num_candidates": %d,
        "filter": {
          "bool": {
            "filter": [ { "terms": { "tour_id": [ %s ] } } ]%s
          }
        }
      }
    }
    """.formatted(
                SIZE_GROUPED,
                terms, mustNot,
                vecJson, KNN_K, KNN_CANDIDATES,
                terms, mustNot
        );
    }

    /* ---------- 파싱/가공 유틸 ---------- */

    // [설명] collapse(field=tour_id)로 "투어당 대표 1문서"만 올라옵니다.
//        여기서는 대표 hit의 _score를 투어 점수로 사용합니다.
//        (inner_hits 평균이 필요하면 averageInnerHitsScore(...)를 사용해 교체 가능)
    private ParseResult parseScores(SearchResponse<Map> resp) {
        Map<Long, Double> tourScore = new LinkedHashMap<>();
        double maxScore = 0.0;
        for (Hit<Map> h : resp.hits().hits()) {
            Long tourId = readTourId(h);                           // [_source → fields] 순으로 안전 추출
            if (tourId == null) continue;
            double s = h.score() == null ? 0.0 : h.score();        // 대표 문서 점수 채택
            tourScore.put(tourId, s);
            if (s > maxScore) maxScore = s;                        // 정규화용 최대값
        }
        int total = resp.hits().total() == null
                ? tourScore.size()
                : (int) resp.hits().total().value();               // 부족 판정에 사용

        ParseResult r = new ParseResult();
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
        if (h.fields() != null && h.fields().get("tour_id") instanceof List<?> list && !list.isEmpty()) {
            Object v = list.get(0);
            if (v instanceof Number n) return n.longValue();
            try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignore) {}
        }
        return null;
    }

    // [설명] 점수 맵을 max 값으로 나눠 0~1 정규화 (max<=0이면 원본 유지)
    private Map<Long, Double> normalize(Map<Long, Double> in) {
        double max = in.values().stream().mapToDouble(d -> d).max().orElse(0.0);
        if (max <= 0) return in;
        Map<Long, Double> out = new HashMap<>(in.size());
        for (var e : in.entrySet()) out.put(e.getKey(), e.getValue() / max);
        return out;
    }

    // [설명] 두 점수 맵을 가중합으로 융합(키 합집합). ES 내부(BM25/Vector) 결합에 사용.
    private Map<Long, Double> fuse(Map<Long, Double> bm25, Map<Long, Double> vec, double wB, double wV) {
        Map<Long, Double> out = new HashMap<>();
        Set<Long> keys = new HashSet<>(); keys.addAll(bm25.keySet()); keys.addAll(vec.keySet());
        for (Long id : keys) {
            double s = wB * bm25.getOrDefault(id, 0.0) + wV * vec.getOrDefault(id, 0.0);
            out.put(id, s);
        }
        return out;
    }

    /* ---------- 문자열/JSON 도우미 ---------- */

    // [설명] BM25 질의문 텍스트: 사용자 원문 + should(가산어) 간단 결합
    private String buildBm25QueryText(String userText, KeywordRule rule) {
        StringBuilder sb = new StringBuilder();
        if (userText != null && !userText.isBlank()) sb.append(userText).append(' ');
        if (rule != null && rule.getKeyword() != null && !rule.getKeyword().isBlank()) sb.append(rule.getKeyword()).append(' ');
        if (rule != null && rule.getShouldList() != null) {
            rule.getShouldList().stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> sb.append(s).append(' '));
        }
        return sb.toString().trim();
    }

    // [설명] kNN 임베딩용 텍스트: 사용자 원문 + should(가산어)만 결합
    private String buildVectorText(String userText, KeywordRule rule) {
        StringBuilder sb = new StringBuilder();
        if (userText != null && !userText.isBlank()) sb.append(userText).append(' ');
        if (rule != null && rule.getShouldList() != null) {
            rule.getShouldList().stream().filter(s -> s != null && !s.isBlank())
                    .forEach(s -> sb.append(s).append(' '));
        }
        return sb.toString().trim();
    }

    // [설명] must_not(제외어) 구성 → combined_text에 OR match로 배제
    private String buildMustNot(KeywordRule rule) {
        if (rule == null || rule.getExcludeList() == null || rule.getExcludeList().isEmpty()) return "";
        String blocks = rule.getExcludeList().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> "{\"match\":{\"combined_text\":{\"query\":\"" + escape(s) + "\",\"operator\":\"or\"}}}")
                .collect(Collectors.joining(","));
        return blocks.isBlank() ? "" : ", \"must_not\":[" + blocks + "]";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").trim();
    }

    private static String toJsonArray(float[] vec) {
        if (vec == null || vec.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        return sb.append(']').toString();
    }

    /* ---------- DTO ---------- */
    public static class HybridResult {
        public Map<Long, Double> scores;    // 최종 Top-N(투어 단위)
        public String bm25QueryJson;        // BM25 요청 원문 JSON
        public String knnQueryJson;         // kNN 요청 원문 JSON(미사용이면 null)
        public boolean usedVector;          // 벡터 사용 여부
        public int bm25Hits;                // BM25 총 히트 수
        public double bm25MaxScore;         // BM25 max_score

        public static HybridResult empty() {
            HybridResult r = new HybridResult();
            r.scores = Collections.emptyMap();
            r.bm25QueryJson = null; r.knnQueryJson = null; r.usedVector = false;
            r.bm25Hits = 0; r.bm25MaxScore = 0.0;
            return r;
        }
    }

    private static class ParseResult {
        Map<Long, Double> tourScores; // 투어 단위 점수 맵
        int totalHits;                // 총 히트(부족 판정용)
        double maxScore;              // 최대 점수(정규화/로그용)
    }


}