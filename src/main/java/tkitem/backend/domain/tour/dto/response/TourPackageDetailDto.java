package tkitem.backend.domain.tour.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;

import java.util.Date;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourPackageDetailDto {
    // 투어 정보
    private Long tourId;
    private String title;
    private String feature;
    private String imgUrl;
    private String provider;

    // 투어 일자 정보
    private Long tourPackageId;
    private Long price;
    private Date departureDate;
    private Date returnDate;
    private String bookingUrl;
    private String departureAirline;
    private String returnAirline;

    // 투어 세부 일정 정보
    List<TourDetailScheduleDto> schedules;
}
