package tkitem.backend.domain.order.service;

import tkitem.backend.domain.order.dto.request.OrderCreateRequest;
import tkitem.backend.domain.order.dto.response.OrderCreateResponse;

public interface OrderService {

    OrderCreateResponse createOrder(Long memberId, OrderCreateRequest request);
    Long getIdByMerchantOrderId(String merchantOrderId);
    int calculateAmount(Long orderId);
    void markPaid(Long orderId, String merchantOrderId, String paymentKey, int paidAmount);
}
