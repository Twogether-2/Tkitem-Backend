package tkitem.backend.domain.product_recommendation.dto.response;

import lombok.*;
import tkitem.backend.domain.product_recommendation.dto.CandidateProductDto;
import tkitem.backend.domain.product_recommendation.dto.ChecklistItemDto;

import java.util.List;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateListResponse {
    private Long tripId;
    private Long checklistItemId;
    private Long productCategorySubId;
    private String itemName;
    private List<String> extractedTagCodes;
    private List<CandidateProductDto> products;

    public static CandidateListResponse of(
            Long tripId,
            ChecklistItemDto it,
            List<String> tags,
            List<CandidateProductDto> products
    ) {
        return CandidateListResponse.builder()
                .tripId(tripId) // <-- 이전 null → 수정
                .checklistItemId(it.getChecklistItemId())
                .productCategorySubId(it.getProductCategorySubId())
                .itemName(it.getItemName())
                .extractedTagCodes(tags)
                .products(products)
                .build();
    }
}
