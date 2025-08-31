package tkitem.backend.domain.recommendation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedPickDto {
    private Long productId;
    private String name;
    private BigDecimal price;
    private Double utility;
    private String matchedTags;
}