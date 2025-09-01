package tkitem.backend.domain.product_recommendation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedPickDto {
    private Long productId;
    private String name;
    private String brandName;
    private String category;
    private String imgUrl;
    private BigDecimal price;
    private Double utility;
    private String matchedTags;
}