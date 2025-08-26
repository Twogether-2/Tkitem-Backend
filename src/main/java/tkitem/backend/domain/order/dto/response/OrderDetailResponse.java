package tkitem.backend.domain.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tkitem.backend.domain.order.dto.OrderItemDetail;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {

    private Long orderId;
    private String status;
    private Integer paidAmount;
    private String createdAt;
    private List<OrderItemDetail> items;
}
