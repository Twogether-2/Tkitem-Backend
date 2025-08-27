package tkitem.backend.domain.scheduleType.mapper;

import org.apache.ibatis.annotations.Mapper;
import tkitem.backend.domain.scheduleType.vo.ScheduleType;

import java.util.List;

@Mapper
public interface ScheduleTypeMapper {
    List<ScheduleType> selectAll();
}
