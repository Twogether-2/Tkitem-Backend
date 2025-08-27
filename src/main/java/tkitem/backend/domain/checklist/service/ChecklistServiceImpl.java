package tkitem.backend.domain.checklist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.checklist.dto.response.ChecklistAiResponseDto;
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
            // 1) 로그에 루트 원인(Oracle ORA-xxxxx 포함) 남기기
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            // log.error 로 남기는 걸 추천
            System.err.println("[AI-Checklist] SP failed: " + t.getClass().getName() + " - " + t.getMessage());

            // 2) 굳이 감싸서 던질 거면 원인 메시지를 붙여주기(운영/개발 정책에 맞게)
            throw new BusinessException(ErrorCode.CHECKLIST_AI_FAILED);
        }
        List<ChecklistItemVo> items = checklistMapper.selectActiveByTrip(tripId);
        return new ChecklistAiResponseDto(tripId, items.size());
    }
}
