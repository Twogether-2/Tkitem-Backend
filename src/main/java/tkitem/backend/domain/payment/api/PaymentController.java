package tkitem.backend.domain.payment.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tkitem.backend.domain.order.service.OrderPaymentFacade;
import tkitem.backend.domain.payment.dto.request.PaymentConfirmRequest;
import tkitem.backend.domain.payment.dto.response.PaymentConfirmResponse;

@RequiredArgsConstructor
@RequestMapping("/payment")
@RestController
public class PaymentController {

    private final OrderPaymentFacade facade;

    @PostMapping("/confirm")
    public PaymentConfirmResponse confirm(@RequestBody PaymentConfirmRequest request) {
        return facade.confirm(request);
    }
}