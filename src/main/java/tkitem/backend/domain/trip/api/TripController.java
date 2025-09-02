package tkitem.backend.domain.trip.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.trip.dto.TripInfoResponse;
import tkitem.backend.domain.trip.service.TripService;
import tkitem.backend.domain.trip.vo.Trip;

@Slf4j
@RequestMapping("/trip")
@RestController
@RequiredArgsConstructor
public class TripController {
	private final TripService tripService;

	@Operation(
	    summary = "내 여행 목록 조회",
	    description = "로그인한 사용자의 여행 목록을 커서 기반 페이징 방식으로 조회"
	)
	@Parameters({
	    @Parameter(name = "limit", description = "가져올 최대 여행 개수", example = "5"),
	    @Parameter(name = "cursorDepartureDate", description = "커서 기준 출발일(yyyy-MM-dd)", example = "2025-09-16"),
	    @Parameter(name = "cursorTripId", description = "커서 기준 여행 ID", example = "100")
	})
	@GetMapping("")
	public ResponseEntity<List<Trip>> getTrips(
		@AuthenticationPrincipal Member member,
		@RequestParam(value = "limit", defaultValue = "5") int limit,
		@RequestParam(value = "cursorDepartureDate") String cursorDepartureDate,
		@RequestParam("cursorTripId") Long cursorTripId){

		List<Trip> result = tripService.getMyTripList(member, cursorDepartureDate, cursorTripId, limit);
		return ResponseEntity.ok(result);
	}

	@Operation(
	    summary = "여행 패키지 상세 조회",
	    description = "여행 ID(tripId)를 받아 해당 여행의 상세 정보를 반환합니다."
	)
	@Parameters({
	    @Parameter(name = "tripId", description = "조회할 여행 ID", example = "1")
	})
	@GetMapping("/{tripId}")
	public ResponseEntity<TripInfoResponse> getTripInfo(@PathVariable Long tripId){
		TripInfoResponse result = tripService.getTripInfo(tripId);
		return ResponseEntity.ok(result);
	}
}
