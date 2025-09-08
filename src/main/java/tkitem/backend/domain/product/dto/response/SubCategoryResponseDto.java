package tkitem.backend.domain.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryResponseDto {
    private Long id;     // product_category_sub_id
    private String name; // 서브 카테고리 명
    private String isProduct; //상품 여부
}