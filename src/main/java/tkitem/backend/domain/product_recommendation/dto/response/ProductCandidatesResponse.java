package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductCandidatesResponse {
    private Long tripId;
    private Long checklistItemId;
    private Long productCategorySubId;
    private String itemName;
    private List<ProductResponse> products;
    private String categoryContext;
}