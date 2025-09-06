package tkitem.backend.domain.checklist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tkitem.backend.domain.checklist.dto.response.AiReasonEnvelope;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;
import tkitem.backend.domain.checklist.mapper.AiReasonMapper;
import tkitem.backend.domain.checklist.vo.AiReasonVo;

@Service
@RequiredArgsConstructor
public class AiReasonServiceImpl implements AiReasonService {

    private final AiReasonMapper aiReasonMapper;
    private final AiReasonAsyncGenerator asyncGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    @Override
    public void regenerate(Long tripId) {
        aiReasonMapper.softDeleteActiveByTrip(tripId);
        triggerAsyncAfterCommit(tripId);
    }

    @Transactional
    @Override
    public AiReasonEnvelope getOrTrigger(Long tripId, boolean triggerIfMissing) {
        AiReasonVo vo = aiReasonMapper.findActiveByTrip(tripId);

        if (vo != null) {
            String st = vo.getStatus();
            switch (st) {
                case "READY":
                    // 본문 파싱
                    if (vo.getContentJson() == null) {
                        return AiReasonEnvelope.error("결과가 비어 있어요. 다시 시도해 주세요.");
                    }
                    try {
                        AiReasonResponse data = objectMapper.readValue(vo.getContentJson(), AiReasonResponse.class);
                        return AiReasonEnvelope.ready(data);
                    } catch (Exception e) {
                        return AiReasonEnvelope.error("결과를 해석하지 못했어요. 다시 시도해 주세요.");
                    }

                case "PROCESSING":
                    // 이미 생성 중
                    return AiReasonEnvelope.processing();

                case "ERROR":
                    if (triggerIfMissing) {
                        // 기존 활성본 정리 후 재시작
                        aiReasonMapper.softDeleteActiveByTrip(tripId);
                        triggerAsyncAfterCommit(tripId);
                        return AiReasonEnvelope.processing();
                    } else {
                        // 메시지 그대로 노출 (컨트롤러에서 202로 내려감)
                        return AiReasonEnvelope.error(
                                vo.getErrorMessage() != null ? vo.getErrorMessage() : "생성에 실패했어요."
                        );
                    }

                default:
                    return AiReasonEnvelope.error("알 수 없는 상태예요.");
            }
        }

        // 활성 레코드 자체 없음
        if (!triggerIfMissing) {
            return AiReasonEnvelope.error("아직 생성되지 않았어요.");
        }

        // 최초 생성 트리거 (PROCESSING 행은 Generator가 선삽입)
        triggerAsyncAfterCommit(tripId);
        return AiReasonEnvelope.processing();
    }

    private void triggerAsyncAfterCommit(Long tripId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncGenerator.generateForTrip(tripId);
                }
            });
        } else {
            asyncGenerator.generateForTrip(tripId);
        }
    }
}
