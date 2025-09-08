package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long productId;
    private String name;
    private String brandName;
    private String category;
    private String imgUrl;
    private BigDecimal price;
    private Double avgReview;
    private String code;
    private String url;
}
