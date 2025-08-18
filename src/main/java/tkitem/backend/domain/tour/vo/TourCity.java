package tkitem.backend.domain.tour.vo;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TourCity {
    private Long tourCityId;
    private Long cityId;
    private Long tourId;
    private int sortOrder; // 방문 순서
    private int nights; // 도시에서 숙박 일수
}
