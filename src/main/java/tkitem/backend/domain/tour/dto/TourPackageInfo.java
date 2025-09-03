package tkitem.backend.domain.tour.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TourPackageInfo {
	private Long tourPackageId;
	private Long tourId;
	private int price;
	private LocalDate departureDate;
	private LocalDate returnDate;
	private String departureAirline;
	private String returnAirline;
	private String packageDateCode; // 현대트레블 travleId 값
	private String bookingUrl; // 예약 페이지
	private String sourceUrl; // 크롤링 원본 url
	private String title;
	private String imgUrl;
}
