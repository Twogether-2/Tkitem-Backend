package tkitem.backend.domain.order.service;

import tkitem.backend.domain.order.dto.request.OrderCreateRequest;
import tkitem.backend.domain.order.dto.response.OrderCreateResponse;
import tkitem.backend.domain.order.dto.response.OrderDetailResponse;
import tkitem.backend.domain.order.dto.response.OrderSummaryResponse;

import java.util.List;

public interface OrderService {

    OrderCreateResponse createOrder(Long memberId, OrderCreateRequest req);
    int calculateAmount(Long orderId);
    void markPaid(Long orderId, int paidAmount);
    void markCanceled(Long orderId);
    OrderSummaryResponse findOrdersByMemberId(Long memberId);
    OrderDetailResponse findOrderDetail(Long orderId);
}
