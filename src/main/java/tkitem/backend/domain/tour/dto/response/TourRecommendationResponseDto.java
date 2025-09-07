package tkitem.backend.domain.tour.dto.response;

import lombok.*;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourRecommendationResponseDto {
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

    // 추천된 투어 그룹 정보
    private Long groupId;

    // 투어 세부 일정 정보
    List<TourDetailScheduleDto> schedules;

    // 투어 추천 점수 정보
    private Double finalScore;
    private Double dbScore;
    private Double esScore;
    // 투어 추천 이유 분석해서 데이터화 해서 전달 필요
}
