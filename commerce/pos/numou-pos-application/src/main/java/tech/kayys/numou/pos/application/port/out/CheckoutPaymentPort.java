package tech.kayys.numou.pos.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Narrow POS consumer contract mapped by composition to Finance Payment's public API. */
public interface CheckoutPaymentPort {
    PaymentSnapshot createOrGet(PaymentRequest request);
    Optional<PaymentSnapshot> find(String tenantId, String paymentId);

    record PaymentRequest(String tenantId, String idempotencyKey, String checkoutReference,
            BigDecimal amount, String currency, String description) {
        public PaymentRequest {
            if (tenantId == null || tenantId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()
                    || checkoutReference == null || checkoutReference.isBlank() || amount == null || amount.signum() <= 0
                    || currency == null || !currency.matches("[A-Z]{3}"))
                throw new IllegalArgumentException("invalid checkout payment request");
        }
    }
    record PaymentSnapshot(String tenantId, String paymentId, BigDecimal amount, String currency, Status status) {
        public PaymentSnapshot {
            if (tenantId == null || tenantId.isBlank() || paymentId == null || paymentId.isBlank()
                    || amount == null || amount.signum() <= 0 || currency == null || !currency.matches("[A-Z]{3}") || status == null)
                throw new IllegalArgumentException("invalid payment snapshot");
        }
    }
    enum Status { CREATED, REQUIRES_CUSTOMER_ACTION, PENDING, SUCCEEDED, FAILED, CANCELLED, EXPIRED, UNKNOWN }
}
