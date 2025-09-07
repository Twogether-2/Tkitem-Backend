package tkitem.backend.domain.checklist.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChecklistItemRow {
    private Long   checklistItemId;
    private Long   tripId;
    private Long   productCategorySubId;
    private String itemName;
    private Integer scheduleDate;
    private Double score;
    private String tier;
    private String notes;
    private String source;
}
