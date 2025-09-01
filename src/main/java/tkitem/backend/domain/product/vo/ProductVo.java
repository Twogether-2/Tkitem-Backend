package tkitem.backend.domain.product.vo;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductVo {
    private Long productId;
    private Long categoryId;
    private String brandName;
    private Double avgReview;
    private String name;
    private Long price;
    private String code;
    private String url;
    private String imgUrl;
}