package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trip {
    private Long tripId;
    private Long memberId;
    private Long tourPackageId;
    private String title;
    private LocalDate departureDate;
    private LocalDate arrivalDate;
    private String type;
}