package tkitem.backend.domain.checklist.dto.response;

import lombok.*;
import tkitem.backend.domain.checklist.vo.ChecklistItemVo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItemResponseDto {
    private Long checklistItemId;
    private Long tripId;
    private Long productCategorySubId;
    private String itemName;
    private Integer scheduleDate;
    private BigDecimal score;
    private String tier;
    private String notes;
    private String source;
    private boolean checked;
    private boolean purchased;
    private OffsetDateTime createdAt;

    public static ChecklistItemResponseDto from(ChecklistItemVo v) {
        return ChecklistItemResponseDto.builder()
                .checklistItemId(v.getChecklistItemId())
                .tripId(v.getTripId())
                .productCategorySubId(v.getProductCategorySubId())
                .itemName(v.getItemName())
                .scheduleDate(v.getScheduleDate())
                .score(v.getScore())
                .tier(v.getTier())
                .notes(v.getNotes())
                .source(v.getSource())
                .checked("T".equalsIgnoreCase(trim(v.getIsChecked())))
                .purchased("T".equalsIgnoreCase(trim(v.getIsPurchased())))
                .createdAt(v.getCreatedAt() == null ? null :
                        v.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime())
                .build();
    }

    private static String trim(String s) { return s == null ? null : s.trim(); }
}