package tkitem.backend.domain.cart.service;

import tkitem.backend.domain.cart.dto.request.CartItemsCreateRequest;
import tkitem.backend.domain.cart.dto.response.CartItemsCreateResponse;

import java.util.List;

public interface CartService {

    List<CartItemsCreateResponse> addItems(Long memberId, CartItemsCreateRequest req);
}
