package tkitem.backend.domain.payment.enums;

public enum PaymentRefundStatus {
    REQUESTED,  // 환불 요청됨
    APPROVED,   // 환불 승인
    REJECTED,   // 환불 거절
    FAILED      // 환불 실패
}