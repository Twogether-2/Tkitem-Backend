package tkitem.backend.domain.product_recommendation.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BudgetRecommendationRequest {
    private List<Long> checklistItemIds;
    private BigDecimal budget;
}