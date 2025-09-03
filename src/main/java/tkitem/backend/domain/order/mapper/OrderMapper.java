package tkitem.backend.domain.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.order.dto.OrderItemDetail;
import tkitem.backend.domain.order.dto.TripOrderGroup;
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

    // 회원의 모든 주문 (헤더 + 여행 그룹 + 아이템)
    List<OrderDetailResponse> findOrdersByMemberId(@Param("memberId") Long memberId);

    // 중첩 매핑용: 주문별 여행 그룹 목록
    List<TripOrderGroup> findTripGroupsByOrderId(@Param("orderId") Long orderId);

    // 중첩 매핑용: (orderId, tripId)별 아이템 목록
    List<OrderItemDetail> findOrderItemsByOrderAndTrip(
            @Param("orderId") Long orderId,
            @Param("tripId") Long tripId);

    Optional<OrderDetailResponse> findOrderDetail(@Param("orderId") Long orderId);
}
