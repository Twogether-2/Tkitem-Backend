package tkitem.backend.domain.tour.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourDetailScheduleDto {
    private Long tourDetailScheduleId;
    private Long cityId;
    private String countryName;
    private String cityName;
    private String title;
    private String description;
    private Integer sortOrder;
    private String defaultType;
    private int scheduleDay;
}
