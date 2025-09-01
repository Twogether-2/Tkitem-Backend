package tkitem.backend.domain.recommendation.dto.response;

import lombok.*;
import tkitem.backend.domain.recommendation.dto.ScheduleGroupDto;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRecommendationResponse {
    private Long tripId;
    private BigDecimal budget;
    private BigDecimal totalCost;
    private Double totalUtility;
    private BigDecimal remaining;
    private List<ScheduleGroupDto> groups;
}