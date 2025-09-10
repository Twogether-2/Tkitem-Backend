package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@Builder
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
    private String recommendReason;
    private Double matchScore;
    private List<String> matchedTags;
}
