package tkitem.backend.domain.trip.service;

import java.util.List;

import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.trip.vo.Trip;

public interface TripService {
	List<Trip> getMyTripList(Member member,
		String cursorDepartureDate,
		Long cursorTripId,
		int limit);
}
