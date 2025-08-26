package tkitem.backend.domain.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    void insertReady(@Param("orderId") Long orderId,
                     @Param("provider") String provider,
                     @Param("status") String status,
                     @Param("amount") Integer amount,
                     @Param("currency") String currency,
                     @Param("merchantOrderId") String merchantOrderId);

    Long findOrderIdByMerchantOrderId(@Param("merchantOrderId") String merchantOrderId);
    Long findPaymentIdByMerchantOrderId(@Param("merchantOrderId") String merchantOrderId);

    void updateApproved(@Param("paymentId") Long paymentId,
                        @Param("method") String method,
                        @Param("paymentKey") String paymentKey,
                        @Param("approvedAt") String approvedAt,
                        @Param("amount") Integer amount);

    void updateCanceled(@Param("paymentId") Long paymentId);

    Long findPaymentIdByPaymentKey(@Param("paymentKey") String paymentKey);

    Long findOrderIdByPaymentKey(@Param("paymentKey") String paymentKey);
}