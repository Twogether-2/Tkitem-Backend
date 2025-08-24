package tkitem.backend.domain.scheduleType.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Getter
@Service
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEsDocumentDto {
    private String tourDetailScheduleId;
    private String tourId;
    private String scheduleDate;
    private String countryName;
    private String cityName;
    private String title;
    private String description;
    private float[] embedding;
}
