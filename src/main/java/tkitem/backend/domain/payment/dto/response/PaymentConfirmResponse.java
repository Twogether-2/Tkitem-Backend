package tkitem.backend.domain.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmResponse {

    private String paymentKey;
    private String orderId;
    private Integer paidAmount;
    private String approvedAt;
    private String method;
}
