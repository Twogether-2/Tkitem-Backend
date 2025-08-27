package tkitem.backend.domain.checklist.vo;

import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItemVo {
    private Long checklistItemId;
    private Long tripId;
    private Long productCategorySubId;
    private String itemName;
    private Integer scheduleDate;     // NULL이면 TRIP 공통
    private BigDecimal score;
    private String tier;
    private String notes;
    private String source;            // 'AI' | 'USER'
    private String isChecked;         // 'T' | 'F'
    private String isPurchased;       // 'T' | 'F'
    private Timestamp createdAt;
    private Timestamp updatedAt;
}