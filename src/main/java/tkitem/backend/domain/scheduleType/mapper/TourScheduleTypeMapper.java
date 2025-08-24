package tkitem.backend.domain.scheduleType.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TourScheduleTypeMapper {

    // SCHEDULE_TYPE 이름으로 ID 조회
    Long findScheduleTypeByName(@Param("name") String name);

    // TOUR_DETAIL_TYPE upsert용
    int upsertTourScheduleTypeIdByName(@Param("name") String name,
                                       @Param("typeId") Long scheduleTypeId,
                                       @Param("score") Double score);
}
