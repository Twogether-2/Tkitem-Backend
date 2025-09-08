package tkitem.backend.domain.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tkitem.backend.domain.cart.dto.CartProductTripItemDto;

import java.util.List;

@Getter
@AllArgsConstructor
public class CartProductTripListResponse {
    private final List<CartProductTripItemDto> items;
}