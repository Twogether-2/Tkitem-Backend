package tkitem.backend.domain.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.order.dto.OrderItemDetail;

import java.util.List;

@Mapper
public interface OrderMapper {

    // 주문 마스터 insert
    void insertOrder(@Param("memberId") Long memberId,
                     @Param("merchantOrderId") String merchantOrderId,
                     @Param("status") String status);

    // merchantOrderId 로 PK 조회
    Long findOrderIdByMerchantOrderId(@Param("merchantOrderId") String merchantOrderId);

    // 총 금액 합계
    Integer sumOrderAmount(@Param("orderId") Long orderId);

    // 주문 아이템 목록
    List<OrderItemDetail> findOrderItems(@Param("orderId") Long orderId);
}
