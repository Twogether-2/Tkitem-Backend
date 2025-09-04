package tkitem.backend.domain.checklist.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.checklist.dto.ChecklistItemRow;
import tkitem.backend.domain.checklist.dto.TripMeta;
import tkitem.backend.domain.checklist.dto.TripPlace;
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
    List<ChecklistItemVo> selectChecklistByTrip(@Param("tripId") Long tripId, @Param("day") Integer day,
                                                @Param("checked") Boolean checked, @Param("isProduct") Boolean isProduct);

    //카테고리 존재 개수
    int countProductCategorySubs(@Param("ids") List<Long> productCategorySubIds);

    //체크리스트 수기 등록
    void createChecklist(@Param("tripId") Long tripId, @Param("memberId") Long memberId,
                         @Param("scheduleDate") Integer normalizedDay,
                         @Param("ids") List<Long> productCategorySubIds);

    //체크리스트 단건 삭제
    int softDeleteById(Long checklistItemId, Long memberId);

    //체크리스트 전체 삭제(초기화)
    int softDeleteAllActiveByTrip(Long tripId, Long memberId);

    //체크리스트 체크(활성/비활성)
    int setCheckedById(@Param("checklistItemId") Long checklistItemId,
                       @Param("checked") boolean checked,
                       @Param("memberId") Long memberId);

    // 여행 총 일 수 조회
    Integer getTripTotalDays(@Param("tripId") Long tripId);

    //여행의 AI체크리스트 조회
    List<ChecklistItemRow> selectChecklistItemsByTrip(@Param("tripId") Long tripId);

    // 여행 메타(월/일수)
    TripMeta selectTripMeta(@Param("tripId") Long tripId);

    // 일정의 도시/국가 목록
    List<TripPlace> selectTripPlaces(@Param("tripId") Long tripId);
}
