package tkitem.backend.domain.trip.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.city.mapper.CityMapper;
import tkitem.backend.domain.city.vo.City;
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
	private final CityMapper cityMapper;

	@Override
	public List<Trip> getMyTripList(Member member, String cursorDepartureDate, Long cursorTripId, int limit) {
		log.info("[TripService] getMyTripList : cursorTripId = {}, limit = {}", cursorTripId, limit);

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

		List<City> cities = cityMapper.findCitiesByTourPackageId(tourPackageId);

		// 방문도시들로 제목 만들기
		String title = createTitle(cities);

		tripCreateResponseDto.setMemberId(member.getMemberId());
		tripCreateResponseDto.setTitle(title);

		tripMapper.insertTrip(tripCreateResponseDto);

		return tripCreateResponseDto;
	}

	private String createTitle(List<City> cities){

		// 대한민국 포함 거르기
		cities = cities.stream()
				.filter(c -> c.getCountryName() == null || !"대한민국".equals(c.getCountryName().trim()))
				.toList();

		if(cities.isEmpty()) return "즐거운 여행";

		// 도시명 합산 길이
		int cityNameLen = cities.stream().map(City::getCityName).distinct().mapToInt(String::length).sum();

		// 방문도시 이름 합친게 10자 이내면 그냥 만들기
		if(cityNameLen <= 10) {
			return cities.stream().map(City::getCityName).distinct().collect(Collectors.joining(", "))+" 여행";
		}

		// 10자 이상이고 방문나라가 동일하면 대표 나라면 넣기
		if(cities.stream().map(City::getCountryName).distinct().count() == 1){
			return cities.get(0).getCountryName()+" "+cities.get(0).getCityName()+" 여행";
		}

		// 나라가 1~3개면 나라명 합산
		long distinctCountryCount = cities.stream().map(City::getCountryName).distinct().count();
		if(distinctCountryCount <= 3){
			return cities.stream().map(City::getCountryName).distinct().collect(Collectors.joining(", "))+" 여행";
		}

		// 나라가 4개 이상이면 대륙명 + N개국 여행 으로 반환
		return cities.get(0).getCountryGroupName()+" "+distinctCountryCount+"개국 여행";
	}
}