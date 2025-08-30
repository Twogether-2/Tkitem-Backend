package tkitem.backend.domain.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryListResponseDto {
    private Long mainId;        // product_category_main_id
    private String mainName;
    private List<SubCategoryResponseDto> items;
}
