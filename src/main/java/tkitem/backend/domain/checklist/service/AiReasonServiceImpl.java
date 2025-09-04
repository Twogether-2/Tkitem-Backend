package tkitem.backend.domain.checklist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import tkitem.backend.domain.checklist.dto.ChecklistItemRow;
import tkitem.backend.domain.checklist.dto.TripMeta;
import tkitem.backend.domain.checklist.dto.TripPlace;
import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;
import tkitem.backend.domain.checklist.mapper.ChecklistMapper;
import tkitem.backend.domain.checklist.vo.ReasonContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReasonServiceImpl implements AiReasonService {

    private final ChatClient chatClient;
    private final ChecklistMapper checklistMapper;
    private final ReasonContextBuilder contextBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    @Override
    public AiReasonResponse generate(Long tripId) {
        // 1) 데이터 로드
        List<ChecklistItemRow> items   = checklistMapper.selectChecklistItemsByTrip(tripId);
        TripMeta meta                  = checklistMapper.selectTripMeta(tripId);
        List<TripPlace> places         = checklistMapper.selectTripPlaces(tripId);

        // 2) 컨텍스트 생성
        ReasonContext ctx = contextBuilder.build(tripId, items, meta, places);

        // 3) 프롬프트 로드 + 컨텍스트 JSON 바인딩
        String base = readClasspath("prompts/checklist-reason.md");
        String ctxJson;
        try {
            ctxJson = objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize context", e);
        }
        String finalPrompt = base.replace("{contextJson}", ctxJson);

        // 4) 모델 호출 → JSON 파싱
        return chatClient.prompt()
                .user(u -> u.text(finalPrompt))
                .call()
                .entity(AiReasonResponse.class);
    }

    private String readClasspath(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read prompt: " + path, e);
        }
    }
}
