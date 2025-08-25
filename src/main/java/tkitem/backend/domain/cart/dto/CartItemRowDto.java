package tkitem.backend.domain.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemRowDto {

    private Long cartItemId;
    private Long productId;
    private Integer quantity;
}