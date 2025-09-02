package tkitem.backend.domain.trip.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Trip {
	private Long tripId;
	private String imgUrl;
	private String title;
	private String departureDate;
	private String arrivalDate;
	private int dDay;
	private String status; // 다가오는 여행(UPCOMING), 지나간 여행(PAST), 여행 중(ONGOING)
}
