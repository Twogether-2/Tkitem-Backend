package tkitem.backend.domain.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripOrderGroup {
    private Long tripId;
    private String tripTitle;
    private List<OrderItemDetail> items;
}