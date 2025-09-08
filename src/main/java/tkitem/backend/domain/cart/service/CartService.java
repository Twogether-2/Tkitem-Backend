package tkitem.backend.domain.cart.service;

import tkitem.backend.domain.cart.dto.response.*;
import tkitem.backend.domain.cart.dto.request.CartItemQuantityUpdateRequest;
import tkitem.backend.domain.cart.dto.request.CartItemsCreateRequest;

import java.util.List;

public interface CartService {

    List<CartItemsCreateResponse> addItems(Long memberId, CartItemsCreateRequest req);
    CartListResponse getCart(Long memberId, boolean hasTripParam, Long tripIdOrNull);
    CartItemUpdateResponse changeQuantity(Long memberId, Long cartItemId, CartItemQuantityUpdateRequest request);
    CartItemUpdateResponse deleteCartItem(Long memberId, Long cartItemId);
    CartTripListResponse getTripsForCart(Long memberId);

    CartProductTripListResponse getTripIdsByProduct(Long memberId, Long productId);
}
