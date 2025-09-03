package tkitem.backend.domain.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tkitem.backend.domain.order.dto.OrderItemDetail;
import tkitem.backend.domain.order.dto.TripOrderGroup;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {
    private Long orderId;
    private String status;
    private BigDecimal paidAmount;
    private String createdAt;
    private List<TripOrderGroup> order;  
}