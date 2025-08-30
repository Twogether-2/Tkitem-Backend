package tkitem.backend.domain.payment.enums;

public enum PaymentStatus {
    READY,      // 결제 준비
    APPROVED,   // 승인됨
    CANCELED,   // 취소됨
    FAILED,     // 실패
    REFUNDED    // 환불됨
}