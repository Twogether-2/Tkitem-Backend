package tkitem.backend.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.payment.dto.request.PaymentConfirmRequest;
import tkitem.backend.domain.payment.dto.response.PaymentConfirmResponse;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;
import tkitem.backend.infra.toss.TossPaymentsClient;
import tkitem.backend.infra.toss.TossPaymentsResponse;

@RequiredArgsConstructor
@Service
public class OrderPaymentFacade {

    private final OrderService orderService;
    private final TossPaymentsClient tossClient;

    @Transactional
    public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {
        final String merchantOrderId = request.getOrderId();
        final String paymentKey = request.getPaymentKey();

        Long orderPk = orderService.getIdByMerchantOrderId(merchantOrderId);
        int serverAmount = orderService.calculateAmount(orderPk);
        if (request.getAmount() != serverAmount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        TossPaymentsResponse response = tossClient.confirm(paymentKey, merchantOrderId, serverAmount);
        orderService.markPaid(orderPk, merchantOrderId, response.getPaymentKey(), response.getTotalAmount());

        return new PaymentConfirmResponse(response.getPaymentKey(), response.getOrderId(),
                response.getTotalAmount(), response.getApprovedAt(), response.getMethod());
    }
}