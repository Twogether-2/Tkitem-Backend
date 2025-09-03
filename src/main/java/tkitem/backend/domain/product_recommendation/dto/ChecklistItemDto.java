package tkitem.backend.domain.product_recommendation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistItemDto {
    private Long checklistItemId;
    private Long productCategorySubId; // PRODUCT.CATEGORY_ID
    private String itemName;
    private Integer scheduleDate;      // null 가능
    private BigDecimal score;
    private String tier;
    private String notes;
}
