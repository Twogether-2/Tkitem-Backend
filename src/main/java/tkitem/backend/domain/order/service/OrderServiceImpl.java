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
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.util.List;

@RequiredArgsConstructor
@Transactional
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderCreateResponse createOrder(Long memberId, OrderCreateRequest request) {
        String merchantOrderId = "order-" + java.util.UUID.randomUUID();

        orderMapper.insertOrder(memberId, merchantOrderId, OrderStatus.PENDING.name());
        Long orderId = orderMapper.findOrderIdByMerchantOrderId(merchantOrderId);
        request.getItems().forEach(it ->
                orderItemMapper.insertOrderItem(orderId, it.getProductId(), it.getQuantity(), OrderStatus.PENDING.name())
        );

        int amount = orderMapper.sumOrderAmount(orderId);

        List<OrderItemDetail> items = orderMapper.findOrderItems(orderId);
        String orderName = request.getItems().isEmpty() ? "주문상품"
                : items.get(0).getProductName() +
                (items.size() > 1 ? " 외 " + (items.size() - 1) + "건" : "");

        return new OrderCreateResponse(merchantOrderId, amount, orderName);
    }

    @Transactional(readOnly = true)
    @Override
    public Long getIdByMerchantOrderId(String merchantOrderId) {
        Long id = orderMapper.findOrderIdByMerchantOrderId(merchantOrderId);
        if (id == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        return id;
    }

    @Transactional(readOnly = true)
    @Override
    public int calculateAmount(Long orderId) {
        return orderMapper.sumOrderAmount(orderId);
    }

    @Override
    public void markPaid(Long orderId, String merchantOrderId, String paymentKey, int paidAmount) {
        orderMapper.updateOrderPaid(merchantOrderId, paidAmount, paymentKey);
        orderMapper.updateOrderItemsStatus(orderId, OrderStatus.PAID.name());
    }

    @Override
    public void markCanceledByPaymentKey(String paymentKey) {
        Long orderId = orderMapper.findOrderIdByPaymentKey(paymentKey);
        if (orderId == null) throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        orderMapper.updateOrderStatus(orderId, OrderStatus.CANCELED.name());
        orderMapper.updateOrderItemsStatus(orderId, OrderStatus.CANCELED.name());
    }
}
