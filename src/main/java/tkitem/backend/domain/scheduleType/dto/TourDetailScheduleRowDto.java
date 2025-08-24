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
    private String tourDetailScheduleId;
    private String tourId;
    private String scheduleDate;
    private String countryName;
    private String cityName;
    private String title;
    private String description;
}
