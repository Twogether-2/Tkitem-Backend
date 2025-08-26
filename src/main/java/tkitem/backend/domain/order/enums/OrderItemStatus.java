package tkitem.backend.domain.order.enums;

public enum OrderItemStatus {
    PENDING,   // 결제 대기
    PAID,      // 결제 완료
    CANCELED,  // 취소
    REFUNDED   // 환불
}