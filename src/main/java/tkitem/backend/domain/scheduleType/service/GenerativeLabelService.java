package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerativeLabelService {

    public record Result(String typeName, double score) {}

    private final EmbeddingService embeddingService;
    private final ElasticsearchClient esClient;

    private static final String LABEL_INDEX = "schedule_type_labels_v1";

    // LLM 호출/프롬프트 작성/스로틀링(동시성·쿼터) 처리
    public Map<Long, Result> classifyBatch(List<TourDetailScheduleRowDto> rows){
        Map<Long, Result> out = new HashMap<>();
        for (TourDetailScheduleRowDto r : rows) {
            try {
                String text = safe(r.getTitle()) + " " + safe(r.getDescription());
                Result res = classifyOne(text); // LLM 호출
                if (res != null) {
                    out.put(Long.valueOf(r.getTourDetailScheduleId()), res);
                }
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        return out;
    }

    // 단건 분류 (LLM 호출부 연결 지점)
    private Result classifyOne(String text) throws Exception {
        // TODO: 여기서 OpenAI/Gemini 호출(WebClient 등) → {typeName, score} JSON 파싱
        // 예시 파싱 결과를 가정:
        String type = pickTypeFromText(text);    // 임시 룰/파서(대체)
        double score = estimateConfidence(text); // 임시 스코어(대체)

        // [검증] 허용 타입만 통과(철자 불일치 방지)
        if (!ALLOWED_TYPES.contains(type)) return null;

        // [클램프] 0.0~1.0
        score = Math.max(0.0, Math.min(1.0, score));
        return new Result(type, score);
    }

    // --- 임시 대체 로직(LLM 붙이기 전까지)
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "FLIGHT","TRANSFER","GUIDE","HOTEL","HOTEL_STAY","SIGHTSEEING","LANDMARK",
            "MUSEUM_HERITAGE","PARK_NATURE","ACTIVITY","HIKING_TREKKING","SHOW",
            "SPA_MASSAGE","SHOPPING","MEAL","CAFE","FREE_TIME","ETC","SWIM_SNORKELING"
    );
    private String pickTypeFromText(String t){
        /* TODO: 간단 키워드 룰 or LLM로 교체 */
        return "SIGHTSEEING";
    }
    private double estimateConfidence(String t){
        /* TODO */
        return 0.72;
    }
    private String safe(String s){
        return s==null? "" : s;
    }
}
