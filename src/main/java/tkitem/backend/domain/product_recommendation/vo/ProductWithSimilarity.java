package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithSimilarity extends Product {
    private String baseName;
    private Double similarity;
}