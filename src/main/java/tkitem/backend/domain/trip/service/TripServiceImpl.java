package tkitem.backend.domain.trip.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.TourPackageInfo;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.domain.trip.dto.TripCreateResponseDto;
import tkitem.backend.domain.trip.dto.TripInfoResponse;
import tkitem.backend.domain.trip.dto.UpcomingTripResponse;
import tkitem.backend.domain.trip.mapper.TripMapper;
import tkitem.backend.domain.trip.vo.Trip;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TripServiceImpl implements TripService{

	private final TripMapper tripMapper;
	private final TourMapper tourMapper;

	@Override
	public List<Trip> getMyTripList(Member member, String cursorDepartureDate, Long cursorTripId, int limit) {
		log.info("[TripService] getMyTripList");

		if(member == null){
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}

		return tripMapper.selectTripsByMemberId(member.getMemberId(), cursorDepartureDate, cursorTripId, limit);
	}

	@Override
	public TripInfoResponse getTripInfo(Long tripId) {

		log.info("[TripService] getTripInfo : tripId = {}", tripId);
		Optional<Trip> result = tripMapper.selectTripInfoByTripId(tripId);

		if(result.isEmpty()){
			throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
		}

		Trip trip = result.get();
		TourPackageInfo tourPackageInfo = null;

		if(result.get().getType().equals("PKG")){
			tourPackageInfo = tourMapper.findTourPackageInfoByTourPackageId(trip.getTourPackageId()).get();
		}

		return new TripInfoResponse(trip, tourPackageInfo);
	}

	@Override
	public List<UpcomingTripResponse> getUpcomingTrips(Member member) {
		if(member == null){
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}
		return tripMapper.selectUpcomingTrips(member.getMemberId());
	}

	@Override
	public TripCreateResponseDto createTrip(Long tourPackageId, Member member) {

		TripCreateResponseDto tripCreateResponseDto = tripMapper.selectTripforCreate(tourPackageId);

		if(tripCreateResponseDto == null) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);

		tripCreateResponseDto.setMemberId(member.getMemberId());

		tripMapper.insertTrip(tripCreateResponseDto);

		return tripCreateResponseDto;
	}
}