package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BudgetRecommendationResponse {
    private Long tripId;
    private BigDecimal budget;
    private List<ScheduleGroupResponse> groups;
    private BigDecimal totalPrice;
    private BigDecimal remainingBudget;
}