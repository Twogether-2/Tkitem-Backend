package tkitem.backend.domain.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemsCreateRequest {

    private Long tripId;

    @NotEmpty
    private List<CartItem> items;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItem {
        @NotNull
        private Long productId;

        @NotNull @Min(1)
        private Integer quantity;
    }
}
