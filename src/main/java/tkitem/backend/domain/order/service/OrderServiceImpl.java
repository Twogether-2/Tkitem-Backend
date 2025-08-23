package tkitem.backend.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.order.dto.OrderItemDetail;
import tkitem.backend.domain.order.dto.request.OrderCreateRequest;
import tkitem.backend.domain.order.dto.response.OrderCreateResponse;
import tkitem.backend.domain.order.enums.OrderStatus;
import tkitem.backend.domain.order.mapper.OrderItemMapper;
import tkitem.backend.domain.order.mapper.OrderMapper;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Transactional
    public OrderCreateResponse createOrder(Long memberId, OrderCreateRequest request) {
        // UUID 기반 merchantOrderId 생성
        String merchantOrderId = "order-" + java.util.UUID.randomUUID();

        // 주문 마스터 insert
        orderMapper.insertOrder(memberId, merchantOrderId, OrderStatus.PENDING.name());
        Long orderId = orderMapper.findOrderIdByMerchantOrderId(merchantOrderId);

        // 아이템 insert
        request.getItems().forEach(it ->
                orderItemMapper.insertOrderItem(orderId, it.getProductId(), it.getQuantity(), OrderStatus.PENDING.name())
        );

        // 총 금액 계산
        int amount = orderMapper.sumOrderAmount(orderId);

        // 대표 상품명 조회 (첫 상품명 + 외 n건)
        List<OrderItemDetail> items = orderMapper.findOrderItems(orderId);
        String orderName = request.getItems().isEmpty() ? "주문상품"
                : items.get(0).getProductName() +
                (items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "");

        return new OrderCreateResponse(merchantOrderId, amount, orderName);
    }

}
