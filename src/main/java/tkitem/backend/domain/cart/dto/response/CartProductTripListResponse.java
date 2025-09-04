package tkitem.backend.domain.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CartProductTripListResponse {
    private final List<Long> tripIds;
}
