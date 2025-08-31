package tkitem.backend.domain.checklist.service;

import jakarta.validation.constraints.NotNull;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistListResponseDto;

import java.util.List;

public interface ChecklistService {
    ChecklistAiResponseDto generateAiChecklist(Long tripId,Long memberId);

    ChecklistListResponseDto getChecklistByTrip(Long tripId, Integer day, Boolean checked, Boolean isProduct);

    void createChecklist(Long tripId, Long memberId, @NotNull List<Long> productCategorySubIds, Integer scheduleDate);

    void deleteChecklistItem(Long checklistItemId, Long memberId);

    int deleteAllActiveByTrip(Long tripId, Long memberId);

    void setChecked(Long checklistItemId, boolean value, Long memberId);
}
