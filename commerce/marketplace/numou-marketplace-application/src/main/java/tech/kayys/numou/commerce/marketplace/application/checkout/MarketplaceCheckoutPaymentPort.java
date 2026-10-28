package tech.kayys.numou.commerce.marketplace.application.checkout;

import java.math.BigDecimal;
import java.time.Instant;

/** Provider-neutral anti-corruption port. UNKNOWN/PENDING are never treated as payment failure. */
public interface MarketplaceCheckoutPaymentPort {
    PaymentResult createOrGet(String tenantId, String idempotencyKey, String orderReference,
                              BigDecimal amount, String currency);
    PaymentResult find(String tenantId, String paymentId);

    enum PaymentState { CREATED, REQUIRES_CUSTOMER_ACTION, PENDING, SUCCEEDED, FAILED, CANCELLED, EXPIRED, UNKNOWN }
    record PaymentResult(String paymentId, PaymentState state, Instant updatedAt) {
        public PaymentResult {
            if (paymentId == null || paymentId.isBlank()) throw new IllegalArgumentException("paymentId is required");
            if (state == null) throw new IllegalArgumentException("state is required");
            if (updatedAt == null) throw new IllegalArgumentException("updatedAt is required");
        }
    }
}
