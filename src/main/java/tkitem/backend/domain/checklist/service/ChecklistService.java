package tkitem.backend.domain.checklist.service;

import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistListResponseDto;

public interface ChecklistService {
    ChecklistAiResponseDto generateAiChecklist(Long tripId,Long memberId);

    ChecklistListResponseDto getChecklistByTrip(Long tripId, Integer day);
}
