package tkitem.backend.domain.checklist.service;

import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;

public interface ChecklistService {
    ChecklistAiResponseDto generateAiChecklist(Long tripId,Long memberId);
}
