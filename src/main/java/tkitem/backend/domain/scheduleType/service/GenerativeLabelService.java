package tkitem.backend.domain.scheduleType.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerativeLabelService {

    public record Result(String typeName, double score) {}

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private final String OPENAI_MODEL = "gpt-4o-mini";

    /**
     * LLM 호출/프롬프트 작성/스로틀링(동시성·쿼터) 처리
     * @param rows
     * @return
     */
    public Map<Long, Result> classifyBatch(List<TourDetailScheduleRowDto> rows){
        Map<Long, Result> out = new HashMap<>();
        for (TourDetailScheduleRowDto r : rows) {
            try {
                String text = safe(r.getTitle()) + " " + safe(r.getDescription());
                Result res = classifyOne(text); // LLM 호출
                if (res != null) {
                    out.put(r.getTourDetailScheduleId(), res);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        return out;
    }

    /**
     * 단건 분류: LLM JSON 응답 기반으로 분류 (실패 시 ETC)
     * @param text
     * @return
     * @throws Exception
     */
    private Result classifyOne(String text) throws Exception {

        String cleaned = (text == null ? "" : text).replaceAll("\\s+", " ").trim();

        // LLM 프롬프트 (허용 라벨만, JSON 한 줄만 반환 강제)
        // TODO : 분류 하는거 보고 프롬프트 조정 필요
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
              - 분류가 불가한 경우 label : etc, confidence: 0.00 으로 분류
              - confidence는 0.0~1.0
              - 여분 텍스트/설명 금지
        """.formatted(String.join(",", ALLOWED_TYPES));

        String user = """
        제목+설명:
        %s
        """.formatted(cleaned);

        ChatClient chat = ChatClient.builder(chatModel).build();
        String json = chat.prompt()
                .system(system)
                .user(user)
                .options(OpenAiChatOptions.builder()
                        .model(OPENAI_MODEL)   // 경량·저비용 모델
                        .temperature(0.1)       // 일관성↑
                        .build())
                .call()
                .content();

        // JSON 파싱 → 라벨/점수
        List<Result> topK = parseJson(json);
        assert topK != null;
        if (topK.isEmpty()) return new Result("ETC", 0.0);
        return topK.getFirst();
    }

    // 허용 타입
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "FLIGHT","TRANSFER","GUIDE","HOTEL","HOTEL_STAY","SIGHTSEEING","LANDMARK",
            "MUSEUM_HERITAGE","PARK_NATURE","ACTIVITY","HIKING_TREKKING","SHOW",
            "SPA_MASSAGE","SHOPPING","MEAL","CAFE","FREE_TIME","ETC","SWIM_SNORKELING"
    );

    private String safe(String s){
        return s==null? "" : s;
    }

    /**
     * LLM 파싱 헬퍼
     * @param json
     * @return
     */
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

        // [추가] 점수 내림차순 정렬 + 중복 라벨 제거(첫 등장 우선)
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        LinkedHashMap<String, Result> dedup = new LinkedHashMap<>();
        for (Result r : out) dedup.putIfAbsent(r.typeName(), r);
        return new ArrayList<>(dedup.values());
    }
}
