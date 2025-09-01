package tkitem.backend.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.order.dto.OrderItemDetail;
import tkitem.backend.domain.order.dto.request.OrderCreateRequest;
import tkitem.backend.domain.order.dto.response.OrderCreateResponse;
import tkitem.backend.domain.order.dto.response.OrderDetailResponse;
import tkitem.backend.domain.order.dto.response.OrderSummaryResponse;
import tkitem.backend.domain.order.enums.OrderItemStatus;
import tkitem.backend.domain.order.enums.OrderStatus;
import tkitem.backend.domain.order.mapper.OrderItemMapper;
import tkitem.backend.domain.order.mapper.OrderMapper;
import tkitem.backend.domain.payment.enums.PaymentStatus;
import tkitem.backend.domain.payment.mapper.PaymentMapper;
import tkitem.backend.domain.product.service.ProductService;
import tkitem.backend.domain.product.vo.ProductVo;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.exception.EntityNotFoundException;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class OrderServiceImpl implements OrderService {

    private static final String TOSS_PROVIDER = "TOSS";

    private final ProductService productService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;

    public OrderCreateResponse createOrder(Long memberId, OrderCreateRequest req) {
        // 1) 주문 헤더
        orderMapper.insertOrder(memberId, OrderStatus.PENDING.name());
        Long orderId = orderMapper.currOrderId();

        // 2) 아이템 (가격/스냅샷 고정)
        String firstName = null;
        for (int i=0; i<req.getItems().size(); i++) {

            OrderCreateRequest.OrderItemRequest item = req.getItems().get(i);
            ProductVo product = productService.getProductById(item.getProductId());

            orderItemMapper.insertOrderItem(
                    orderId,
                    item.getProductId(),
                    item.getTripId(),
                    item.getQuantity(),
                    product.getPrice(),
                    "KRW",
                    product.getName(),
                    product.getImgUrl(),
                    OrderStatus.PENDING.name()
            );
            if (i == 0) {
                firstName = (product.getName() != null ? product.getName() : "주문상품");
            }
        }

        // 3) 합계 & 주문명
        int amount = orderMapper.sumOrderAmount(orderId);
        String orderName = firstName + (req.getItems().size() > 1 ? " 외 " + (req.getItems().size()-1) + "건" : "");

        // 4) Toss용 MERCHANT_ORDER_ID 생성 + PAYMENT READY 선기록
        String merchantOrderId = "order-" + UUID.randomUUID();
        paymentMapper.insertReady(orderId, TOSS_PROVIDER, PaymentStatus.READY.name(), amount, "KRW", merchantOrderId);

        // 5) 응답 (토스 SDK 파라미터)
        return new OrderCreateResponse(merchantOrderId, amount, orderName);
    }

    @Transactional(readOnly = true)
    public int calculateAmount(Long orderId) {
        return orderMapper.sumOrderAmount(orderId);
    }

    public void markPaid(Long orderId, int paidAmount) {
        orderMapper.updateOrderPaid(orderId, paidAmount, OrderStatus.PAID.name());
        orderMapper.updateOrderItemsStatus(orderId, OrderItemStatus.PAID.name());
    }

    public void markCanceled(Long orderId) {
        orderMapper.updateOrderStatus(orderId, OrderStatus.CANCELED.name());
        orderMapper.updateOrderItemsStatus(orderId, OrderItemStatus.CANCELED.name());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findOrdersByMemberId(Long memberId) {
        return orderMapper.findOrdersByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse findOrderDetail(Long orderId) {
        OrderDetailResponse head = orderMapper.findOrderDetail(orderId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ORDER_NOT_FOUND.getMessage(), ErrorCode.ORDER_NOT_FOUND));

        List<OrderItemDetail> items = orderMapper.findOrderItems(orderId);
        return new OrderDetailResponse(head.getOrderId(), head.getStatus(), head.getPaidAmount(), head.getCreatedAt(), items);
    }
}
