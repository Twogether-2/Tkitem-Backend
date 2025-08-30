package tkitem.backend.domain.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateResponse {

    private String orderId;   // 프론트가 토스 SDK 호출 시 사용하는 orderId (merchantOrderId)
    private Integer amount;   // 총 결제 금액
    private String orderName; // 대표 상품명 (ex. 병아리콩 도시락 외 1건)
}
