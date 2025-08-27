package tkitem.backend.domain.checklist.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.checklist.vo.ChecklistItemVo;

import java.util.List;

@Mapper
public interface ChecklistMapper {

    //trip 존재 여부 확인
    int existsTrip(Long tripId);

    //trip데이터 존재 여부 확인
    int existsTripWithPackage(@Param("tripId") Long tripId);

    //체크리스트 자동 세팅
    void generateAiCheckList(@Param("tripId") Long tripId, @Param("memberId") Long memberId);

    //체크리스트 조회
    List<ChecklistItemVo> selectChecklistByTrip(@Param("tripId") Long tripId, @Param("day") Integer day, @Param("checked") Boolean checked);

    //카테고리 존재 개수
    int countProductCategorySubs(@Param("ids") List<Long> productCategorySubIds);

    //체크리스트 수기 등록
    void createChecklist(@Param("tripId") Long tripId, @Param("memberId") Long memberId,
                         @Param("scheduleDate") Integer normalizedDay,
                         @Param("ids") List<Long> productCategorySubIds);
}
