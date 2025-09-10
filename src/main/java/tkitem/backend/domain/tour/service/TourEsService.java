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
            Set<Long> allowTourIds,
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
     * BM25 검색: should 부스팅 + must_not + allow terms 필터
     * @param rule
     * @param allow
     * @return
     */
    public Map<Long, Double> searchBm25Scores(KeywordRule rule, Set<Long> allow) {
        try {
            List<Query> shouldQs = new ArrayList<>();

            // [MOD] shouldList: 정제돼 있다는 전제 → trim/empty 체크 생략
            if (rule.getShouldList() != null) {
                for (String q : rule.getShouldList()) {
                    // 2-1) 일반 키워드: best_fields 멀티매치 (title^2, description)
                    shouldQs.add(QueryBuilders.multiMatch(mm -> mm
                            .query(q)
                            .fields("title^2", "description")
                    ));
                    // 2-2) 복합어(공백 포함)에는 phrase 매치 부스팅 추가
                    if (q.indexOf(' ') >= 0) {
                        shouldQs.add(QueryBuilders.matchPhrase(mp -> mp
                                .field("title")
                                .query(q)
                                .boost(2.5f)
                        ));
                        shouldQs.add(QueryBuilders.matchPhrase(mp -> mp
                                .field("description")
                                .query(q)
                                .boost(1.5f)
                        ));
                    }
                }
            }

            // [MOD] excludeList: 선택적이므로 안전 처리(비어있을 수 있음)
            List<Query> mustNotQs = new ArrayList<>();
            if (rule.getExcludeList() != null) {
                for (String q : rule.getExcludeList()) {
                    if (q == null || q.isBlank()) continue;
                    mustNotQs.add(QueryBuilders.multiMatch(mm -> mm
                            .query(q)
                            .fields("title", "description")
                    ));
                    if (q.indexOf(' ') >= 0) {
                        mustNotQs.add(QueryBuilders.matchPhrase(mp -> mp
                                .field("title").query(q).boost(1.0f)
                        ));
                        mustNotQs.add(QueryBuilders.matchPhrase(mp -> mp
                                .field("description").query(q).boost(1.0f)
                        ));
                    }
                }
            }

            // 위치 필터: country/countryGroup가 빈 문자열이면 allow는 빈/널이므로 필터 생략
            List<Query> filterQs = new ArrayList<>();
            if (allow != null && !allow.isEmpty()) {
                filterQs.add(QueryBuilders.terms(t -> t.field("tour_id").terms(v -> v.value(
                        allow.stream().map(FieldValue::of).toList()
                ))));
            }

            BoolQuery.Builder bool = new BoolQuery.Builder();
            if (!shouldQs.isEmpty()) {
                bool.should(shouldQs);
                bool.minimumShouldMatch("1");
            }
            if (!mustNotQs.isEmpty()) bool.mustNot(mustNotQs);
            if (!filterQs.isEmpty())  bool.filter(filterQs);

            SearchRequest req = SearchRequest.of(s -> s
                    .index(INDEX_TDS)
                    .size(500)
                    .query(q -> q.bool(bool.build()))
                    .sort(sort -> sort.score(sc -> sc.order(SortOrder.Desc)))
                    .source(src -> src.filter(f -> f.includes("tour_id","title")))
            );

            SearchResponse<Map> resp = esClient.search(req, Map.class);

            Map<Long, List<Double>> byTour = new HashMap<>();
            resp.hits().hits().forEach(h -> {
                Map<String, Object> src = h.source();
                if (src == null) return;
                Object tidObj = src.get("tour_id");
                if (tidObj == null) return;
                Long tid = Long.valueOf(String.valueOf(tidObj));
                byTour.computeIfAbsent(tid, k -> new ArrayList<>()).add(h.score());
            });

            Map<Long, Double> out = new HashMap<>();
            for (var e : byTour.entrySet()) {
                double avgTop3 = e.getValue().stream()
                        .sorted(Comparator.reverseOrder())
                        .limit(3)
                        .mapToDouble(Double::doubleValue).average().orElse(0.0);
                out.put(e.getKey(), avgTop3);
            }
            return out;

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("BM25 검색 실패: " + e.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 룰 -> 임베딩 질의 텍스트
     * @param r
     * @return
     */
    public static String buildQueryText(KeywordRule r) { // static으로 공개
        List<String> tokens = new ArrayList<>();
        if (r.getKeyword() != null) tokens.add(r.getKeyword());
        if (r.getShouldList() != null) tokens.addAll(r.getShouldList());
        return String.join(" ", tokens);
    }

    /**
     * BM25 + kNN 결합 스코어 계산 (ES 관련 처리 담당)
     * @param rule
     * @param allow
     * @param k
     * @param candidates
     * @param mTop
     * @param wKnn
     * @param wBm25
     * @return
     */
    public Map<Long, Double> searchByKeywordScores(KeywordRule rule,
                                                   Set<Long> allow,
                                                   int k, int candidates, int mTop,
                                                   double wKnn, double wBm25) {
        try {
            String qText = buildQueryText(rule);
            Map<Long, Double> knn = computeEsScores(qText, (allow == null || allow.isEmpty()) ? null : allow, k, candidates, mTop);
            Map<Long, Double> bm25 = searchBm25Scores(rule, allow);

            Set<Long> all = new HashSet<>();
            all.addAll(knn.keySet());
            all.addAll(bm25.keySet());

            Map<Long, Double> merged = new HashMap<>();
            for (Long tid : all) {
                double sK = knn.getOrDefault(tid, 0.0);
                double sB = bm25.getOrDefault(tid, 0.0);
                merged.put(tid, wKnn * sK + wBm25 * sB);
            }
            return merged;

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("키워드 ES 결합 스코어 계산 실패: " + e.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
