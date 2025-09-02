package tkitem.backend.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDetail {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private String imgUrl;
    private BigDecimal price;
    private Integer quantity;
    private String status;
}
