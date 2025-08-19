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
}
