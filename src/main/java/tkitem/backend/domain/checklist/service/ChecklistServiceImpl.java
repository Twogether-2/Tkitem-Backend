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
        List<ChecklistItemVo> items = checklistMapper.selectChecklistByTrip(tripId, null);
        return new ChecklistAiResponseDto(tripId, items.size());
    }

    @Override
    @Transactional(readOnly = true)
    public ChecklistListResponseDto getChecklistByTrip(Long tripId, Integer day) {
        if (tripId == null || tripId <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (checklistMapper.existsTrip(tripId) == 0) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        if (day != null && day < 0) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);

        List<ChecklistItemVo> items = checklistMapper.selectChecklistByTrip(tripId, day);
        return ChecklistListResponseDto.of(tripId, items);
    }
}
