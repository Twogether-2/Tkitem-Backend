package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MinimumBudgetResponse {
    private BigDecimal minimumBudget;
}
