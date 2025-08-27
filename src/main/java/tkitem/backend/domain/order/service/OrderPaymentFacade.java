package tkitem.backend.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.payment.dto.request.PaymentConfirmRequest;
import tkitem.backend.domain.payment.dto.response.PaymentConfirmResponse;
import tkitem.backend.domain.payment.mapper.PaymentMapper;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.global.error.exception.EntityNotFoundException;
import tkitem.backend.infra.toss.TossPaymentsClient;
import tkitem.backend.infra.toss.TossPaymentsResponse;

@RequiredArgsConstructor
@Transactional
@Service
public class OrderPaymentFacade {

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final TossPaymentsClient tossClient;

    public PaymentConfirmResponse confirm(PaymentConfirmRequest req) {
        final String merchantOrderId = req.getOrderId();
        final String paymentKey = req.getPaymentKey();

        // 1) PAYMENT에서 ORDER_ID 찾기
        Long orderId = paymentMapper.findOrderIdByMerchantOrderId(merchantOrderId);
        if (orderId == null) {
            throw new EntityNotFoundException(ErrorCode.PAYMENT_NOT_FOUND.getMessage(), ErrorCode.PAYMENT_NOT_FOUND);
        }

        // 2) 서버 금액 검증
        int serverAmount = orderService.calculateAmount(orderId);
        if (req.getAmount() != serverAmount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 3) 토스 승인
        TossPaymentsResponse r = tossClient.confirm(paymentKey, merchantOrderId, serverAmount);

        // 4) PAYMENT → APPROVED
        Long paymentId = paymentMapper.findPaymentIdByMerchantOrderId(merchantOrderId);
        paymentMapper.updateApproved(paymentId, r.getMethod(), r.getPaymentKey(), r.getApprovedAt(), r.getTotalAmount());

        // 5) ORDERS/ORDER_ITEM 반영
        orderService.markPaid(orderId, r.getTotalAmount());

        // 6) 커스텀 응답
        return new PaymentConfirmResponse(r.getPaymentKey(), r.getOrderId(), r.getTotalAmount(), r.getApprovedAt(), r.getMethod());
    }

    public void cancel(String paymentKey, String reason) {
        // 1) 토스 취소
        tossClient.cancel(paymentKey, reason);

        // 2) PAYMENT → CANCELED
        Long paymentId = paymentMapper.findPaymentIdByPaymentKey(paymentKey);
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND.getMessage(), ErrorCode.PAYMENT_NOT_FOUND);
        }
        paymentMapper.updateCanceled(paymentId);

        // 3) 주문/아이템 취소 (전체취소 가정)
         Long orderId = paymentMapper.findOrderIdByPaymentKey(paymentKey);
         orderService.markCanceled(orderId);
    }
}