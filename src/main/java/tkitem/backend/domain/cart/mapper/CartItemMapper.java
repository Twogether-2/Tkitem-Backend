package tkitem.backend.domain.cart.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.cart.dto.CartItemRowDto;

@Mapper
public interface CartItemMapper {

    // MERGE 기반 업서트: 동일 (cart_id, product_id, trip_id, status=PENDING) 존재 시 수량 += :quantity, 없으면 INSERT
    int upsertPendingItem(@Param("cartId") Long cartId,
                              @Param("productId") Long productId,
                              @Param("tripId") Long tripId,
                              @Param("quantity") Integer quantity,
                              @Param("actorId") Long actorId);

    // 업서트 후 현재 PENDING 행 조회
    CartItemRowDto selectPendingItem(@Param("cartId") Long cartId,
                                     @Param("productId") Long productId,
                                     @Param("tripId") Long tripId);
}
