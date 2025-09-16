package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long productId;
    private String name;
    private String brandName;
    private String categoryName;
    private String imgUrl;
    private BigDecimal price;
    private Double avgReview;
    private String code;
    private String url;
    private String recommendTokens;
}