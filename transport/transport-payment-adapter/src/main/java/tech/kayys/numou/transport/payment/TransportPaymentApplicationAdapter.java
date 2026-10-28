package tech.kayys.numou.transport.payment;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.transport.spi.TransportPaymentPort;
import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentStatus;

import java.util.Optional;

/** Optional adapter from Transport's narrow payment port to the public Payment application API. */
@ApplicationScoped
@IfBuildProperty(name = "numou.transport.checkout.enabled", stringValue = "true")
public class TransportPaymentApplicationAdapter implements TransportPaymentPort {
    @Inject
    PaymentApplicationPort payments;

    @Override
    public PaymentSnapshot createOrGet(CreatePayment command) {
        PaymentObligation payment = payments.createOrGet(new CreatePaymentObligation(
                command.tenantId(), command.idempotencyKey(), command.callerReference(),
                command.amount(), command.currency(), command.description(), command.metadata()));
        return snapshot(payment);
    }

    @Override
    public PaymentSnapshot find(String tenantId, String paymentId) {
        Optional<PaymentObligation> payment = payments.find(tenantId, paymentId);
        return payment.map(TransportPaymentApplicationAdapter::snapshot)
                .orElseThrow(() -> new IllegalArgumentException("payment was not found in tenant scope"));
    }

    private static PaymentSnapshot snapshot(PaymentObligation payment) {
        return new PaymentSnapshot(payment.tenantId(), payment.paymentId(), payment.callerReference(),
                payment.amount(), payment.currency(), map(payment.status()), payment.actionUri(), payment.updatedAt());
    }

    private static PaymentState map(PaymentStatus status) {
        return switch (status) {
            case CREATED -> PaymentState.CREATED;
            case REQUIRES_CUSTOMER_ACTION -> PaymentState.REQUIRES_CUSTOMER_ACTION;
            case PENDING -> PaymentState.PENDING;
            case SUCCEEDED -> PaymentState.SUCCEEDED;
            case FAILED -> PaymentState.FAILED;
            case CANCELLED -> PaymentState.CANCELLED;
            case EXPIRED -> PaymentState.EXPIRED;
            case UNKNOWN -> PaymentState.UNKNOWN;
            case PARTIALLY_REFUNDED, REFUNDED -> PaymentState.UNKNOWN;
        };
    }
}
