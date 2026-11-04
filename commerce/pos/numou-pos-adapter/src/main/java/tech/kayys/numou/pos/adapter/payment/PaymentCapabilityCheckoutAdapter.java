package tech.kayys.numou.pos.adapter.payment;

import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentStatus;
import tech.kayys.numou.pos.application.port.out.CheckoutPaymentPort;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Maps POS checkout payment operations to the shared Finance Payment capability. */
public final class PaymentCapabilityCheckoutAdapter implements CheckoutPaymentPort {
    private final PaymentApplicationPort payments;

    public PaymentCapabilityCheckoutAdapter(PaymentApplicationPort payments) {
        this.payments = Objects.requireNonNull(payments, "payments");
    }

    @Override
    public PaymentSnapshot createOrGet(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            PaymentObligation payment = payments.createOrGet(new CreatePaymentObligation(
                    request.tenantId(), request.idempotencyKey(), request.checkoutReference(),
                    request.amount(), request.currency(), request.description(),
                    Map.of("capability", "pos", "checkoutReference", request.checkoutReference())));
            return map(payment);
        } catch (PaymentApplicationPort.PaymentOperationRejectedException rejected) {
            throw new PaymentRejectedException("Payment capability rejected checkout obligation", rejected);
        }
    }

    @Override
    public Optional<PaymentSnapshot> find(String tenantId, String paymentId) {
        return payments.find(tenantId, paymentId).map(PaymentCapabilityCheckoutAdapter::map);
    }

    private static PaymentSnapshot map(PaymentObligation payment) {
        Status status = switch (payment.status()) {
            case CREATED -> Status.CREATED;
            case REQUIRES_CUSTOMER_ACTION -> Status.REQUIRES_CUSTOMER_ACTION;
            case PENDING -> Status.PENDING;
            case SUCCEEDED -> Status.SUCCEEDED;
            case FAILED -> Status.FAILED;
            case CANCELLED -> Status.CANCELLED;
            case EXPIRED -> Status.EXPIRED;
            case UNKNOWN, PARTIALLY_REFUNDED, REFUNDED -> Status.UNKNOWN;
        };
        return new PaymentSnapshot(payment.tenantId(), payment.paymentId(), payment.amount(),
                payment.currency(), status);
    }

    public static final class PaymentRejectedException extends RuntimeException {
        public PaymentRejectedException(String message, Throwable cause) { super(message, cause); }
    }
}
