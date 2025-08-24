package tkitem.backend.domain.cart.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.cart.dto.CartInsertDto;
import tkitem.backend.domain.cart.dto.CartItemRowDto;
import tkitem.backend.domain.cart.dto.request.CartItemsCreateRequest;
import tkitem.backend.domain.cart.dto.response.CartItemsCreateResponse;
import tkitem.backend.domain.cart.mapper.CartItemMapper;
import tkitem.backend.domain.cart.mapper.CartMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.exception.InvalidValueException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Transactional
@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    @Override
    public List<CartItemsCreateResponse> addItems(Long memberId, CartItemsCreateRequest request) {
        // TODO: trip 접근 권한 검증
        // TODO: product 존재 검증

        // 같은 productId가 여러 번 오면 합쳐서 DB 라운드트립 최소화
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CartItemsCreateRequest.CartItem item : request.getItems()) {
            Integer quantity = item.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new InvalidValueException(ErrorCode.CART_INVALID_QUANTITY.getMessage(), ErrorCode.CART_INVALID_QUANTITY);
            }
            merged.merge(item.getProductId(), quantity, Integer::sum);
        }

        // 장바구니 확보
        Long cartId = cartMapper.findCartIdByMemberId(memberId);
        if (cartId == null) {
            CartInsertDto cart = new CartInsertDto();
            cart.setMemberId(memberId);
            cart.setCreatedBy(memberId);
            cartMapper.insertCart(cart);
            cartId = cart.getCartId();
        }

        // upsert (MERGE) + 결과 조회
        List<CartItemsCreateResponse> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            Long productId = e.getKey();
            Integer quantity = e.getValue();

            cartItemMapper.upsertPendingItem(cartId, productId, request.getTripId(), quantity, memberId);

            CartItemRowDto row = cartItemMapper.selectPendingItem(cartId, productId, request.getTripId());
            if (row == null) {
                throw new BusinessException("Row Not Found After MERGE", ErrorCode.CART_CONCURRENCY_CONFLICT);
            }

            result.add(new CartItemsCreateResponse(row.getCartItemId(), productId, quantity, row.getQuantity()));
        }

        return result;
    }

}
