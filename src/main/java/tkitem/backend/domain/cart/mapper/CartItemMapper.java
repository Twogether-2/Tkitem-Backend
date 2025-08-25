package tkitem.backend.domain.cart.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.cart.dto.CartItemRowDto;
import tkitem.backend.domain.cart.dto.CartItemRowWithTripDto;

import java.util.List;

@Mapper
public interface CartItemMapper {

    // MERGE 기반 업서트: 동일 (cart_id, product_id, trip_id, status=PENDING) 존재 시 수량 += :quantity, 없으면 INSERT
    int upsertPendingCartItem(@Param("cartId") Long cartId,
                              @Param("productId") Long productId,
                              @Param("tripId") Long tripId,
                              @Param("quantity") Integer quantity,
                              @Param("actorId") Long actorId);

    // 업서트 후 현재 PENDING 행 조회
    CartItemRowDto findPendingItem(@Param("cartId") Long cartId,
                                   @Param("productId") Long productId,
                                   @Param("tripId") Long tripId);

    List<CartItemRowWithTripDto> findPendingItemsWithProduct(
            @Param("cartId") Long cartId,
            @Param("tripId") Long tripId,
            @Param("filterByTrip") boolean filterByTrip
    );
}
