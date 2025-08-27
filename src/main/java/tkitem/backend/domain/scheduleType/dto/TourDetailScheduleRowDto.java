package tkitem.backend.domain.scheduleType.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourDetailScheduleRowDto {
    private Long tourDetailScheduleId;
    private Long tourId;
    private Integer scheduleDate;
    private String countryName;
    private String cityName;
    private Integer sortOrder;
    private String title;
    private String description;
}
