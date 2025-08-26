package tkitem.backend.domain.cart.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CartItemRowWithTripDto {

    private Long tripId;
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String imgUrl;
    private Integer price;
    private Integer quantity;
}