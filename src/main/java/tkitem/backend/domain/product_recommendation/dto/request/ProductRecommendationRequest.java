package tkitem.backend.domain.product_recommendation.dto.request;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class ProductRecommendationRequest {
    private List<Long> checklistItemIds;
    private BigDecimal budget;                // 총예산
    private Weights weights;                  // 선택(없으면 기본 0.7/0.3)

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Weights {
        private Double tag;     // 기본 0.7
        private Double rating;  // 기본 0.3
    }
}