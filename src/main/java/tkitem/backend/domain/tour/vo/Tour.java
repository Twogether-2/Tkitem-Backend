package tkitem.backend.domain.tour.vo;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Tour {
    private Long tourId;
    private String title;
    private String provider;
    private String tripCode;
    private int durationDays;
    private int nights;
    private String bookingUrl; // 예약 페이지
    private String sourceUrl; // 크롤링 원본 url
    private String itineraryJson; // 원본 json 값
    private String feature;
    private String summary;
    private int hotelRating;
    private String imgUrl;
}
