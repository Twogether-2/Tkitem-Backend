package tkitem.backend.domain.order.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    private List<OrderItemRequest> items;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        private Long productId;
        private Integer quantity;
        private Long tripId;
    }
}