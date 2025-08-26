package tkitem.backend.domain.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.order.dto.OrderItemDetail;
import tkitem.backend.domain.order.dto.response.OrderDetailResponse;
import tkitem.backend.domain.order.dto.response.OrderSummaryResponse;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    void insertOrder(@Param("memberId") Long memberId,
                     @Param("status") String status);

    Long currOrderId();

    void updateOrderPaid(@Param("orderId") Long orderId,
                         @Param("paidAmount") Integer paidAmount,
                         @Param("status") String status);

    void updateOrderStatus(@Param("orderId") Long orderId,
                           @Param("status") String status);

    void updateOrderItemsStatus(@Param("orderId") Long orderId,
                                @Param("status") String status);

    Integer sumOrderAmount(@Param("orderId") Long orderId);

    List<OrderSummaryResponse> findOrdersByMemberId(@Param("memberId") Long memberId);
    Optional<OrderDetailResponse> findOrderDetail(@Param("orderId") Long orderId);
    List<OrderItemDetail> findOrderItems(@Param("orderId") Long orderId);
}
