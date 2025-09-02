package tkitem.backend.domain.trip.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
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

	@Override
	public List<Trip> getMyTripList(Member member, String cursorDepartureDate, Long cursorTripId, int limit) {
		log.info("[TripService] getMyTripList");

		if(member == null){
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}

		return tripMapper.selectTripsByMemberId(member.getMemberId(), cursorDepartureDate, cursorTripId, limit);
	}
}
