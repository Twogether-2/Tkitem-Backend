package tkitem.backend.domain.checklist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
import tkitem.backend.domain.checklist.dto.response.ChecklistListResponseDto;
import tkitem.backend.domain.checklist.mapper.ChecklistMapper;
import tkitem.backend.domain.checklist.vo.ChecklistItemVo;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ChecklistServiceImpl implements ChecklistService {

    private final ChecklistMapper checklistMapper;

    @Override
    @Transactional
    public ChecklistAiResponseDto generateAiChecklist(Long tripId, Long memberId) {
        if (tripId == null || tripId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (checklistMapper.existsTrip(tripId) == 0) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }

        if (checklistMapper.existsTripWithPackage(tripId) == 0) {
            throw new BusinessException(ErrorCode.TRIP_PACKAGE_REQUIRED);
        }
        try {
            checklistMapper.generateAiCheckList(tripId,memberId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHECKLIST_AI_FAILED);
        }
        List<ChecklistItemVo> items = checklistMapper.selectChecklistByTrip(tripId, null, null, null);
        return new ChecklistAiResponseDto(tripId, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ChecklistListResponseDto getChecklistByTrip(Long tripId, Integer day, Boolean checked, Boolean isProduct) {
        if (tripId == null || tripId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (checklistMapper.existsTrip(tripId) == 0) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        if (day != null && day < 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        List<ChecklistItemVo> items = checklistMapper.selectChecklistByTrip(tripId, day, checked, isProduct);
        return ChecklistListResponseDto.of(tripId, items);
    }

    @Override
    @Transactional
    public void createChecklist(Long tripId, Long memberId, List<Long> productCategorySubIds, Integer scheduleDate) {
        if (tripId == null || tripId <= 0 || memberId == null || memberId <= 0)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (productCategorySubIds == null || productCategorySubIds.isEmpty())
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (scheduleDate != null && scheduleDate < 0)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (checklistMapper.existsTrip(tripId) == 0)
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);

        int ok = checklistMapper.countProductCategorySubs(productCategorySubIds);
        if (ok != productCategorySubIds.size())
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        Integer normalizedDay = (scheduleDate != null && scheduleDate == 0) ? null : scheduleDate;

        checklistMapper.createChecklist(tripId, memberId, normalizedDay, productCategorySubIds);
    }

    @Override
    @Transactional
    public void deleteChecklistItem(Long checklistItemId, Long memberId) {
        if (checklistItemId == null || checklistItemId <= 0 || memberId == null || memberId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int updated = checklistMapper.softDeleteById(checklistItemId, memberId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Override
    @Transactional
    public int deleteAllActiveByTrip(Long tripId, Long memberId) {
        if (tripId == null || tripId <= 0 || memberId == null || memberId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (checklistMapper.existsTrip(tripId) == 0) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return checklistMapper.softDeleteAllActiveByTrip(tripId, memberId);
    }

    @Override
    @Transactional
    public void setChecked(Long checklistItemId, boolean checked, Long memberId) {
        if (checklistItemId == null || checklistItemId <= 0 || memberId == null || memberId <= 0)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        int updated = checklistMapper.setCheckedById(checklistItemId, checked, memberId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
