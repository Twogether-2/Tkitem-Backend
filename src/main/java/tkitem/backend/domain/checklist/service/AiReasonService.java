package tkitem.backend.domain.checklist.service;

import tkitem.backend.domain.checklist.dto.response.AiReasonEnvelope;
import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;

public interface AiReasonService {

    // ai_reason 재 생성
    void regenerate(Long tripId);

    // aireason 조회
    AiReasonEnvelope getOrTrigger(Long tripId, boolean triggerIfMissing);
}
