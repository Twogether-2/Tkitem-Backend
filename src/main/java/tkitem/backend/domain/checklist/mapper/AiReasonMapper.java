package tkitem.backend.domain.checklist.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.checklist.vo.AiReasonVo;

@Mapper
public interface AiReasonMapper {

    AiReasonVo findActiveByTrip(@Param("tripId") Long tripId);

    int softDeleteActiveByTrip(@Param("tripId") Long tripId);

    int insertProcessing(AiReasonVo entity);

    int updateReady(@Param("aiReasonId") Long aiReasonId,
                    @Param("contentJson") String contentJson);

    int updateError(@Param("aiReasonId") Long aiReasonId,
                    @Param("errorMessage") String errorMessage);
}