package tkitem.backend.domain.cart.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.cart.dto.*;
import tkitem.backend.domain.cart.dto.request.CartItemQuantityUpdateRequest;
import tkitem.backend.domain.cart.dto.request.CartItemsCreateRequest;
import tkitem.backend.domain.cart.dto.response.CartItemUpdateResponse;
import tkitem.backend.domain.cart.dto.response.CartItemsCreateResponse;
import tkitem.backend.domain.cart.dto.response.CartListResponse;
import tkitem.backend.domain.cart.mapper.CartItemMapper;
import tkitem.backend.domain.cart.mapper.CartMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.exception.EntityNotFoundException;
import tkitem.backend.global.error.exception.InvalidValueException;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Transactional
@Service
public class CartServiceImpl implements CartService {

    private static final String DEFAULT_CART_TITLE = "기본 장바구니";

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

            cartItemMapper.upsertPendingCartItem(cartId, productId, request.getTripId(), quantity, memberId);
            CartItemRowDto row = cartItemMapper.findPendingItem(cartId, productId, request.getTripId())
                    .orElseThrow(() -> new BusinessException("Row Not Found After MERGE", ErrorCode.CART_CONCURRENCY_CONFLICT));

            result.add(new CartItemsCreateResponse(row.getCartItemId(), productId, quantity, row.getQuantity()));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CartListResponse getCart(Long memberId, boolean hasTripParam, Long tripIdOrNull) {
        Long cartId = cartMapper.findCartIdByMemberId(memberId);
        List<CartSectionDto> sections = new ArrayList<>();

        // 장바구니 자체가 없을 때
        if (cartId == null) {
            sections.add(buildSection(tripIdOrNull, defaultTitle(tripIdOrNull), List.of()));
            return new CartListResponse(sections);
        }

        // 데이터 조회
        List<CartItemRowWithTripDto> rows = cartItemMapper
                .findPendingItemsWithProduct(cartId, tripIdOrNull, hasTripParam);

        // 아이템 -> 단순 아이템 DTO
        List<CartItemDto> items = rows.stream()
                .map(this::toItem)
                .toList();

        if (hasTripParam) {
            // 파라미터가 있으면 해당 섹션만
            sections.add(buildSection(tripIdOrNull, defaultTitle(tripIdOrNull), items));
            return new CartListResponse(sections);
        }

        // 파라미터가 없으면 기본 장바구니 + 아이템 존재하는 trip 섹션들
        // 1) 기본 장바구니
        List<CartItemDto> baseItems = rows.stream()
                .filter(r -> r.getTripId() == null)
                .map(this::toItem)
                .toList();
        sections.add(buildSection(null, DEFAULT_CART_TITLE, baseItems));

        // 2) tripId별 그룹핑 (null 제외)
        Map<Long, List<CartItemDto>> byTrip = rows.stream()
                .filter(r -> r.getTripId() != null)
                .collect(Collectors.groupingBy(
                        CartItemRowWithTripDto::getTripId,
                        Collectors.mapping(this::toItem, Collectors.toList())
                ));

        // 3) 정렬하여 섹션 생성 (id 내림차순)
        byTrip.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder())) // TODO 필요 시 여행 날짜 정렬 로직으로 교체
                .forEach(e -> sections.add(buildSection(e.getKey(), defaultTitle(e.getKey()), e.getValue())));

        return new CartListResponse(sections);
    }

    @Override
    public CartItemUpdateResponse changeQuantity(Long memberId, Long cartItemId, CartItemQuantityUpdateRequest request) {
        Integer quantity = request.getQuantity();
        if (quantity == null || quantity < 0) {
            throw new InvalidValueException(ErrorCode.CART_INVALID_QUANTITY.getMessage(), ErrorCode.CART_INVALID_QUANTITY);
        }

        int affected;
        if (quantity == 0) { // 0 -> 소프트 삭제
            affected = cartItemMapper.deleteCartItem(memberId, cartItemId, memberId);
        } else { // 절댓값으로 변경
            affected = cartItemMapper.updateCartItemQuantity(memberId, cartItemId, quantity, memberId);
        }
        if (affected == 0) {
            throw new EntityNotFoundException(cartItemId + " is Not Found", ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 결과 스냅샷 반환
        CartItemUpdateResponse response = cartItemMapper.findCartItemSnapshot(memberId, cartItemId)
                .orElseThrow(() -> new EntityNotFoundException(cartItemId + " is Not Found", ErrorCode.CART_ITEM_NOT_FOUND));

        return response;
    }

    private CartItemDto toItem(CartItemRowWithTripDto r) {
        CartItemDto d = new CartItemDto(r.getCartItemId(), r.getProductId(),
                r.getProductName(), r.getImgUrl(), r.getPrice(), r.getQuantity());
        return d;
    }

    private CartSectionDto buildSection(Long tripId, String title, List<CartItemDto> items) {
        return new CartSectionDto(tripId, title, List.copyOf(items));
    }

    private String defaultTitle(Long tripId) {
        if (tripId == null) return DEFAULT_CART_TITLE;
        // TODO: TripMapper 붙이면 실제 이름 사용
        return "여행 장바구니 #" + tripId;
    }

}
