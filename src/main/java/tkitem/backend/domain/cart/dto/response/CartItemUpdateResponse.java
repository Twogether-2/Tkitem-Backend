package tkitem.backend.domain.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemUpdateResponse {

    private Long cartItemId;
    private Long productId;
    private String status;
    private Integer quantity;
}