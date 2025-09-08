package tkitem.backend.domain.product_recommendation.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRecommendationResultDto {
    private Long checklistItemId;
    private PlannedPickDto product;    // null = 미선택
}