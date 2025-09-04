package tkitem.backend.domain.checklist.service;

import tkitem.backend.domain.checklist.dto.response.AiReasonResponse;

public interface AiReasonService {
    AiReasonResponse generate(Long tripId);
}
