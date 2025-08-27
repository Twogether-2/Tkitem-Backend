package tkitem.backend.domain.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartSectionDto {

    private Long tripId;
    private String tripTitle;
    private List<CartItemDto> items = new ArrayList<>();
}