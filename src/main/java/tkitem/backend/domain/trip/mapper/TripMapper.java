package tkitem.backend.domain.trip.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import tkitem.backend.domain.trip.dto.TripCreateResponseDto;
import tkitem.backend.domain.trip.dto.UpcomingTripResponse;
import tkitem.backend.domain.trip.vo.Trip;

@Mapper
public interface TripMapper {

	List<Trip> selectTripsByMemberId(
		@Param("memberId") Long memberId,
		@Param("cursorDepartureDate") String cursorDepartureDate,
		@Param("cursorTripId") Long cursorTripId,
		@Param("limit") int limit
	);

	Optional<Trip> selectTripInfoByTripId(@Param("tripId") Long tripId);

	// 오늘 기준 남은 여행 목록 조회(출발일 가까운 순)
	List<UpcomingTripResponse> selectUpcomingTrips(@Param("memberId") Long memberId);

	TripCreateResponseDto selectTripforCreate(@Param("tourPackageId") Long tourPackageId);

	void insertTrip(TripCreateResponseDto tripCreateResponseDto);
}