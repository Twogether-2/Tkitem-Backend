package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ScheduleGroupResponse {
    private Integer scheduleDate;
    private List<ChecklistProductResponse> items;
}