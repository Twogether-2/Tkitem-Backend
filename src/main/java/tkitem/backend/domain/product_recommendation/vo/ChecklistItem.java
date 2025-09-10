package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItem {
    private Long checklistItemId;
    private Long tripId;
    private Long productCategorySubId;
    private String itemName;
    private Integer scheduleDate;
    private Double score;
    private String tier;
    private String notes;
    private String source;
    private String isChecked;
    private String isPurchased;
    private String isDeleted;
}