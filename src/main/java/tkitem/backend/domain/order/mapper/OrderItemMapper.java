package tkitem.backend.domain.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderItemMapper {
    void insertOrderItem(@Param("orderId") Long orderId,
                         @Param("productId") Long productId,
                         @Param("quantity") Integer quantity,
                         @Param("status") String status);
}