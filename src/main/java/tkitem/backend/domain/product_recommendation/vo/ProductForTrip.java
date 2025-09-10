package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductForTrip extends Product {
    private Double itemScore;
    private String tier;
}
