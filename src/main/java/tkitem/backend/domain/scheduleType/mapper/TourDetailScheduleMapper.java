package tkitem.backend.domain.scheduleType.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;

import java.util.List;

@Mapper
public interface TourDetailScheduleMapper {

    /**
     * Oracle TOUR_DETAIL_SCHEDULE 에서 ES 색인을 위한 레코드 묶음 조회. 배치 전용 SELECT
     * @param offset
     * @param limit
     * @return
     */
    List<TourDetailScheduleRowDto> selectBatchForIndexing(@Param("offset") int offset, @Param("limit") int limit);
}
