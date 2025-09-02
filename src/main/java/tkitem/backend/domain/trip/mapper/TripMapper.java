package tkitem.backend.domain.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.trip.vo.Trip;

@Mapper
public interface TripMapper {
	List<Trip> selectTripsByMemberId(
		@Param("memberId") Long memberId,
		@Param("cursorDepartureDate") String cursorDepartureDate,
		@Param("cursorTripId") Long cursorTripId,
		@Param("limit") int limit
	);
}
