package tkitem.backend.domain.tour.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationInfo {
    @Schema(description = "국가그룹명(한글)", example = "동남아")
    private String countryGroup;

    @Schema(description = "국가명(한글)", example = "베트남")
    private String country;

    @Schema(description = "도시명(한글)", example = "다낭")
    private String city;
}
