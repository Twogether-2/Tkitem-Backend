package tkitem.backend.domain.city.vo;

import lombok.*;

import java.sql.Timestamp;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class City {
    private Long cityId;
    private String countryGroupName;
    private String countryName;
    private String cityName;
    private Timestamp createdAt;
    private Long createdBy;
    private Timestamp updatedAt;
    private Long updatedBy;
}
