package tkitem.backend.domain.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartTripDto {

    private Long tripId;
    private String tripTitle;
    private String imgUrl;
}
