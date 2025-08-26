package tkitem.backend.domain.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryResponse {

    private Long orderId;
    private String status;
    private Integer paidAmount;
    private String createdAt;
}
