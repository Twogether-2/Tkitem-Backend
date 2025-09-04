package tkitem.backend.domain.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CartProductTripItemDto {
    private final Long tripId;     // 0 = 기본 장바구니
    private final Long cartItemId;
}
