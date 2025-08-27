package tkitem.backend.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDetail {

    private Long orderItemId;
    private Long productId;
    private String productName;
    private Integer price;
    private Integer quantity;
    private String status;
}
