package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ES KNN Top-N 재정렬, LLM Top-3 폐쇄 라벨 보완
 */
@Service
@Slf4j
public class GenerativeLabelService {

    public GenerativeLabelService(@Qualifier("geminiChatClient") ChatClient chatClient, ObjectMapper objectMapper, EmbeddingService embeddingService, ElasticsearchClient esClient) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.esClient = esClient;
    }

    public record Result(String typeName, double score) {}

    @Qualifier("geminiChatClient")
    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final ElasticsearchClient esClient;

    private final String GEMINI_MODEL = "gemini-2.0-flash";
    private static final String LABEL_INDEX = "schedule_type_labels_v1";

    /**
     * 코사인 유사도 분류가 애매할 때 LLM 호출/프롬프트 작성/스로틀링(동시성·쿼터) 처리
     * @param rows
     * @return
     */
    public Map<Long, List<Result>> classifyBatchByLLM(List<TourDetailScheduleRowDto> rows){
        Map<Long, List<Result>> out = new ConcurrentHashMap<>();
        if(rows == null || rows.isEmpty()) return out;

        for (TourDetailScheduleRowDto r : rows) {
            try {
                String text = (safe(r.getTitle()) + " " + safe(r.getDescription())).replaceAll("\\s+", " ").trim();
                log.info("[LLM][TRY] tdsId={} model={} textLen={}", r.getTourDetailScheduleId(), GEMINI_MODEL, text.length());

                List<Result> res = classifyTopKByLLM(text, r); // LLM 호출
                log.info("[LLM][RES] tdsId={} topK={}", r.getTourDetailScheduleId(), formatResults(res));

                if (res != null && !res.isEmpty()) {
                    out.put(r.getTourDetailScheduleId(), res);
                }
            } catch (Exception e) {
                log.warn("classifyBatchTopK failed: tdsId={} err={}", r.getTourDetailScheduleId(), e.toString());
            }
        }
        return out;
    }

    /**
     * 1차 필터링 값이 애매할 시 임베딩 유사도 기반 분류
     * @param rows
     * @return
     */
    public Map<Long, List<Result>> classifyBatchByKNN(List<TourDetailScheduleRowDto> rows){
        Map<Long, List<Result>> out = new ConcurrentHashMap<>();
        if(rows == null || rows.isEmpty()) return out;

        for(TourDetailScheduleRowDto r : rows){
            try{
                String text = (safe(r.getTitle()) + " " + safe(r.getDescription())).replaceAll("\\s+", " ").trim();

                log.info("[KNN][TRY] tdsId={} textLen={}", r.getTourDetailScheduleId(), text.length());

                List<Result> topK = classifyTopKByKNN(text);

                log.info("[KNN][RES] tdsId={} topK={}", r.getTourDetailScheduleId(), formatResults(topK));

                if(topK != null && !topK.isEmpty()) out.put(r.getTourDetailScheduleId(), topK);
            } catch (Exception e) {
                log.warn("classifyBatchByKnn failed: tdsId={} err={}", r.getTourDetailScheduleId(), e.toString());
            }
        }
        return out;
    }

    // 단건 분류: LLM JSON 응답 기반으로 분류 (실패 시 ETC). 0~3개까지 분류됨
    private List<Result> classifyTopKByLLM(String text, TourDetailScheduleRowDto tdsRowDto) throws Exception {

        String cleaned = (text == null ? "" : text).replaceAll("\\s+", " ").trim();

        // LLM 프롬프트 (허용 라벨만, JSON 한 줄만 반환 강제)
        String system = """
                당신은 여행 일정(TDS)을 분류하는 분류기입니다.
              아래 허용 라벨 중에서만 고르세요: %s
              반드시 아래 "정확한 형식"의 JSON 배열만 한 줄로 반환하세요.
              정확한 형식 예시:
              [
                {"label":"HOTEL","confidence":0.91},
                {"label":"MEAL","confidence":0.64},
                {"label":"SHOPPING","confidence":0.33}
              ]
              규칙:
              - 최대 3개, confidence 내림차순
              - 분류가 불가한 경우 인터넷 검색을 통해 분류가 무엇인지 인터넷 검색으로 판단
              - 검색 결과로도 분류가 불가한 경우 단일 요소 [{"label":"ETC","confidence":0.00}]로만 반환(다른 라벨과 혼용 금지).
              - confidence는 0.0~1.0
              - 여분 텍스트/설명 금지
              
              특수 규칙(중요):
              - 입력의 default_type이 "MEAL"이면, 최상위 라벨은 반드시 "MEAL"이어야 함.
              - default_type=MEAL인 경우, 다음 요인에 따라 MEAL의 confidence를 가산:
                - 지역 특산품(예: 해당 지역 고유 음식/재료/대표 메뉴) 언급: +0.15
                - “현지 분위기에 어울림”을 강하게 시사(로컬 맛집, 현지 인기, 지역 축제 음식 등): +0.10
                - 프리미엄(파인다이닝/고급/미쉐린급/고가 코스/고가 재료 등)인 경우: +0.20
                - 한국에서도 흔하게 맛볼 수 있는 음식이면 -0.10
              - 가산 후 confidence는 1.0을 넘지 않게 클램프.
              - MEAL 이외 라벨의 confidence는 텍스트 근거가 약하면 낮춰 판단.
              - “ETC”는 다른 라벨이 존재할 때 함께 넣지 말 것.
              - 여분 텍스트/설명 출력 금지(반드시 JSON 배열 한 줄).
        """.formatted(String.join(",", ALLOWED_TYPES));

        String user = """
        다음은 분류 대상 텍스트와 컨텍스트입니다.
        [text]
        제목+설명: %s
        
        [context]
        default_Type: %s,
        region_contry: %s,
        region_city: %s
        """.formatted(
                cleaned,
                tdsRowDto.getDefaultType(),
                tdsRowDto.getCountryName(),
                tdsRowDto.getCityName()
        );

        String json = chatClient.prompt()
                .system(system)
                .user(user)
                .call()
                .content();

        // JSON 파싱 → 라벨/점수
        List<Result> topK = parseJson(json);
        log.info("[LLM][TOPK] textLen={} parsedTopK={}", cleaned.length(), formatResults(topK));
        return (topK == null) ? java.util.Collections.emptyList() : topK;
    }


    // 단건 KNN Top-3 출력 : 라벨 인덱스에서 임베딩 KNN -> (코사인 유사도 * 가중치) 정렬 상위 최대 3개까지 return
    public List<Result> classifyTopKByKNN(String text) throws Exception{
        String cleaned = (text == null ? "" : text).replaceAll("\\s+", " ").trim();

        // 1. 임베딩 생성
        float[] qv = embeddingService.embed(cleaned);
        List<Float> q = new ArrayList<>(qv.length);
        for(float v : qv) q.add(v);

        // 2. ES KNN 검색(라벨 인덱스)
        SearchRequest request = new SearchRequest.Builder()
                .index(LABEL_INDEX)
                .knn(kn -> kn
                        .field("embedding")
                        .queryVector(q)
                        .k(5)
                        .numCandidates(10))
                .source(s -> s.filter(f->f.includes("label", "embedding")))
                .build();

        SearchResponse<Map> resp = esClient.search(request, Map.class);

        // 3. (코사인 유사도 * 가중치)로 최종 점수 계산
        List<Result> out = new ArrayList<>();
        if(resp.hits() != null && resp.hits().hits() != null){
            for(var h : resp.hits().hits()){
                Map<String, Object> src = h.source();
                if(src == null) continue;

                float[] ev = toFloatArray(src.get("embedding"));
                double cos = cosine(qv, ev); // 쿼리 벡터와 문서 벡터 간의 유사도

                Object labelObj = src.get("label");
                if (labelObj instanceof List<?> labelList) {
                    for (Object item : labelList) {
                        if (item instanceof Map<?, ?> labelMap) {
                            String name = (String) labelMap.get("name");
                            double weight = (double) labelMap.get("weight");

                            if (name != null && ALLOWED_TYPES.contains(name)) {
                                double finalScore = cos * weight; // 최종 점수 = 유사도 * 가중치
                                out.add(new Result(name, Math.max(0.0, Math.min(1.0, finalScore))));
                            }
                        }
                    }
                }
            }
        }

        // 4. 최종 점수 기준 정렬 및 상위 3개 추출
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        LinkedHashMap<String, Result> dedup = new LinkedHashMap<>();
        for(Result r : out) dedup.putIfAbsent(r.typeName(), r); // 동일 타입이면 높은 점수 유지
        List<Result> top3 = new ArrayList<>(dedup.values()).subList(0, Math.min(3, dedup.size()));
        log.info("[KNN][TOPK] out top3={}", formatResults(top3));
        return top3;
    }


    // 허용 타입
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "FLIGHT","TRANSFER","GUIDE","HOTEL","HOTEL_STAY","SIGHTSEEING","LANDMARK",
            "MUSEUM_HERITAGE","PARK_NATURE","ACTIVITY","HIKING_TREKKING","SHOW",
            "SPA_MASSAGE","SHOPPING","MEAL","CAFE","FREE_TIME","SNORKELING", "SWIM", "REST"
    );

    private String safe(String s){
        return s==null? "" : s;
    }

    // LLM 파싱 헬퍼
    private List<Result> parseJson(String json) {
        List<Result> out = new ArrayList<>();
        try {
            var node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (int i = 0; i < node.size() && out.size() < 3; i++) {
                    var n = node.get(i);
                    String label = n.path("label").asText(null);
                    double conf = n.path("confidence").asDouble(Double.NaN);
                    if (label == null || !ALLOWED_TYPES.contains(label)) continue;
                    if (java.lang.Double.isNaN(conf)) conf = 0.0;
                    conf = Math.max(0.0, Math.min(1.0, conf)); // 0~1 클램프
                    out.add(new Result(label, conf));
                }
            } else if (node.isObject()) { // 단건 객체로 오는 예외 케이스 호환
                String label = node.path("label").asText(null);
                double conf = node.path("confidence").asDouble(0.0);
                if (label != null && ALLOWED_TYPES.contains(label)) {
                    out.add(new Result(label, Math.max(0.0, Math.min(1.0, conf))));
                }
            }
        } catch (Exception e) {
            log.debug("parseJson failed: {}", e.toString());
            return null;
        }

        // 점수 내림차순 정렬 + 중복 라벨 제거(첫 등장 우선)
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        LinkedHashMap<String, Result> dedup = new LinkedHashMap<>();
        for (Result r : out) dedup.putIfAbsent(r.typeName(), r);
        return new ArrayList<>(dedup.values());
    }

    // 코사인 유사도 계산. Σ(a*b)/(√(Σ(a^2))*√(Σ(b^2)))
    private static double cosine(float[] a, float[] b) {
        double dot=0, na=0, nb=0;
        for (int i=0;i<a.length;i++){ dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
        if (na==0 || nb==0) return 0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }

    // ES _source → float[] 변환 유틸
    private static float[] toFloatArray(Object o){
        if (o instanceof List<?> list) {
            float[] f = new float[list.size()];
            for (int i=0;i<list.size();i++) f[i] = ((Number)list.get(i)).floatValue();
            return f;
        }
        return new float[0];
    }

    //결과 포맷 유틸(로그용)
    private static String formatResults(List<Result> list){
        if (list == null || list.isEmpty()) return "[]";
        return list.stream()
                .map(r -> r.typeName() + "=" + String.format(Locale.ROOT, "%.2f", r.score()))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
