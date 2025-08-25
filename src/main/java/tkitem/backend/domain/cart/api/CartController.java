package tkitem.backend.domain.cart.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.cart.dto.request.CartItemsCreateRequest;
import tkitem.backend.domain.cart.dto.response.CartItemsCreateResponse;
import tkitem.backend.domain.cart.dto.response.CartListResponse;
import tkitem.backend.domain.cart.service.CartService;
import tkitem.backend.domain.member.vo.Member;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/cart")
@RestController
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    @Operation(summary = "장바구니 상품 추가", description = "사용자의 장바구니에 상품을 추가합니다. (동일 상품 + 동일 여행 섹션이 PENDING 상태로 이미 있으면 수량만 증가합니다)")
    public ResponseEntity<?> addItems(@AuthenticationPrincipal Member member,
                                      @RequestBody @Validated CartItemsCreateRequest request) {
        List<CartItemsCreateResponse> response = cartService.addItems(member.getMemberId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니를 조회합니다.")
    @GetMapping("/items")
    public ResponseEntity<CartListResponse> getItems(
            @AuthenticationPrincipal Member member,
            @RequestParam(value = "tripId", required = false) String tripId
    ) {
        boolean hasTripParam = tripId != null;
        Long tripIdOrNull = null;
        if (hasTripParam && !"null".equalsIgnoreCase(tripId)) {
            tripIdOrNull = Long.valueOf(tripId);
        }
        return ResponseEntity.ok(cartService.getCart(member.getMemberId(), hasTripParam, tripIdOrNull));
    }
}