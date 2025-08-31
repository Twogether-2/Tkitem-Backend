package tkitem.backend.domain.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tkitem.backend.domain.cart.dto.CartTripDto;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartTripListResponse {

    List<CartTripDto> trips;
}
