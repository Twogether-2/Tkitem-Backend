package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChecklistProductResponse {
    private Long checklistItemId;
    private ProductResponse product;
}