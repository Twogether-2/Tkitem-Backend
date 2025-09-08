package tkitem.backend.domain.checklist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StreamUtils;
import tkitem.backend.domain.checklist.dto.ChecklistItemRow;
import tkitem.backend.domain.checklist.dto.TripMeta;
import tkitem.backend.domain.checklist.dto.TripPlace;
import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;
import tkitem.backend.domain.checklist.mapper.AiReasonMapper;
import tkitem.backend.domain.checklist.mapper.ChecklistMapper;
import tkitem.backend.domain.checklist.vo.AiReasonVo;
import tkitem.backend.domain.checklist.vo.ReasonContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReasonAsyncGenerator {

    private final ChatClient chatClient;
    private final ChecklistMapper checklistMapper;
    private final AiReasonMapper aiReasonMapper;
    private final ReasonContextBuilder contextBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("aiReasonExecutor")
    public void generateForTrip(long tripId) {
        Long rowId = null;
        try {
            // 1) 컨텍스트 로드/빌드
            List<ChecklistItemRow> items = checklistMapper.selectChecklistItemsByTrip(tripId);
            TripMeta meta = checklistMapper.selectTripMeta(tripId);
            List<TripPlace> places = checklistMapper.selectTripPlaces(tripId);

            ReasonContext ctx = contextBuilder.build(tripId, items, meta, places);
            String ctxJson = objectMapper.writeValueAsString(ctx);
            String ctxHash = sha256(ctxJson);

            // 2) PROCESSING 행 선삽입 (id 확보)
            AiReasonVo row = new AiReasonVo();
            row.setTripId(tripId);
            row.setCtxHash(ctxHash);
            aiReasonMapper.insertProcessing(row);
            rowId = row.getAiReasonId();

            // 3) 프롬프트 작성
            String base = readClasspath("prompts/checklist-reason.md");
            String finalPrompt = base.replace("{contextJson}", ctxJson);

            // 4) LLM 호출 → 결과 파싱
            AiReasonResponse result = chatClient
                    .prompt()
                    .user(u -> u.text(finalPrompt))
                    .call()
                    .entity(AiReasonResponse.class);

            if (result == null || result.getHeadline() == null) {
                throw new IllegalStateException("AI response invalid: headline missing");
            }

            // 5) READY 업데이트
            String outJson = objectMapper.writeValueAsString(result);
            aiReasonMapper.updateReady(rowId, outJson);

            log.info("[AI-REASON] tripId={} generated, rowId={}", tripId, rowId);
        } catch (Exception e) {
            log.error("[AI-REASON] generation failed for tripId={}, rowId={}, cause={}", tripId, rowId, e.toString(), e);
            if (rowId != null) {
                try {
                    aiReasonMapper.updateError(rowId, abbreviate(e.toString(), 990));
                } catch (Exception ignore) { }
            }
        }
    }

    private String readClasspath(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read prompt: " + path, e);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // fallback (충분)
            return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String abbreviate(String src, int max) {
        if (src == null) return null;
        return src.length() <= max ? src : src.substring(0, max);
    }
}
