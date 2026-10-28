package tech.kayys.numou.commerce.marketplace.application.checkout;

import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts the public Payment capability to marketplace checkout.
 *
 * <p>This class delegates idempotency, persistence, tenant checks, provider
 * execution, and verified outcome processing to Payment. It does not create
 * or update payment records directly.</p>
 */
public final class PaymentCapabilityCheckoutAdapter implements MarketplaceCheckoutPaymentPort {
    private final PaymentApplicationPort payments;

    public PaymentCapabilityCheckoutAdapter(PaymentApplicationPort payments) {
        this.payments = Objects.requireNonNull(payments, "payments");
    }

    @Override
    public PaymentResult createOrGet(String tenantId, String idempotencyKey, String orderReference,
                                     BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        PaymentObligation obligation = payments.createOrGet(new CreatePaymentObligation(
                tenantId, idempotencyKey, orderReference, amount, currency,
                "Marketplace order " + orderReference,
                Map.of("capability", "marketplace", "orderReference", orderReference)));
        return map(obligation);
    }

    @Override
    public Optional<PaymentResult> find(String tenantId, String paymentId) {
        return payments.find(tenantId, paymentId).map(PaymentCapabilityCheckoutAdapter::map);
    }

    private static PaymentResult map(PaymentObligation obligation) {
        PaymentState state = switch (obligation.status()) {
            case CREATED -> PaymentState.CREATED;
            case REQUIRES_CUSTOMER_ACTION -> PaymentState.REQUIRES_CUSTOMER_ACTION;
            case PENDING -> PaymentState.PENDING;
            case SUCCEEDED -> PaymentState.SUCCEEDED;
            case FAILED -> PaymentState.FAILED;
            case CANCELLED -> PaymentState.CANCELLED;
            case EXPIRED -> PaymentState.EXPIRED;
            // Refund lifecycle is not evidence that the original checkout payment failed.
            case PARTIALLY_REFUNDED, REFUNDED, UNKNOWN -> PaymentState.UNKNOWN;
        };
        Instant updatedAt = obligation.updatedAt();
        return new PaymentResult(obligation.paymentId(), state, updatedAt);
    }
}
