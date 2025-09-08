package tkitem.backend.domain.tour.dto.response;

import lombok.*;
import tkitem.backend.domain.tour.dto.LocationInfo;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourCommonRecommendDto {
    private Long tourId;
    private String title;
    private String feature;
    private String imgUrl;
    private String provider;
    private Long durationDays;
    private Long nights;

    private String realTitle;

    private Long tourPackageId;
    private Long price;
    private Date departureDate;
    private Date returnDate;
    private String bookingUrl;
    private String departureAirline;
    private String returnAirline;

    List<LocationInfo> locations;
}
