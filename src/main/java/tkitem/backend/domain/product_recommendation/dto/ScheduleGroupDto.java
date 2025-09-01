package tkitem.backend.domain.product_recommendation.dto;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleGroupDto {
    private Integer scheduleDate;                 // null 가능
    private List<ItemRecommendationResultDto> items;
}