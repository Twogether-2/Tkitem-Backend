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
    void generateAiCheckList(@Param("tripId") Long tripId);

    //활성 체크리스트 조회
    List<ChecklistItemVo> selectActiveByTrip(@Param("tripId") Long tripId);
}
