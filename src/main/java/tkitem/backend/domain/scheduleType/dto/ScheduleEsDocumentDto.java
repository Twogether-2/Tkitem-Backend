package tkitem.backend.domain.scheduleType.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEsDocumentDto {
    private Long tourDetailScheduleId;
    private Long tourId;
    private Integer scheduleDate;
    private String countryName;
    private String cityName;
    private Integer sortOrder;
    private String title;
    private String description;
    private float[] embedding;
}
