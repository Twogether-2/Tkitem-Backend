package tkitem.backend.domain.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {

    private Long cartItemId;
    private Long productId;
    private String productName;
    private String imgUrl;
    private Integer price;
    private Integer quantity;
}