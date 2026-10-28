package tech.kayys.numou.transport.payment;

import org.junit.jupiter.api.Test;
import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentStatus;
import tech.kayys.payment.api.RefundOperation;
import tech.kayys.payment.api.RequestPaymentRefund;
import tech.kayys.payment.api.VerifiedPaymentOutcome;
import tech.kayys.numou.transport.spi.TransportPaymentPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TransportPaymentApplicationAdapterTest {
    @Test
    void mapsTransportCommandToPublicPaymentContractAndPreservesActionUri() {
        AtomicReference<CreatePaymentObligation> observed = new AtomicReference<>();
        TransportPaymentApplicationAdapter adapter = new TransportPaymentApplicationAdapter();
        adapter.payments = new StubPayments() {
            @Override
            public PaymentObligation createOrGet(CreatePaymentObligation command) {
                observed.set(command);
                return payment(command.tenantId(), "pay-1", command.callerReference(),
                        command.amount(), command.currency(), PaymentStatus.REQUIRES_CUSTOMER_ACTION,
                        "https://payments.example/action");
            }
        };

        TransportPaymentPort.PaymentSnapshot result = adapter.createOrGet(
                new TransportPaymentPort.CreatePayment("tenant-1", "idem-1", "checkout-1",
                        new BigDecimal("125000.00"), "IDR", "Transport booking checkout",
                        Map.of("checkoutId", "checkout-1")));

        assertThat(observed.get().tenantId()).isEqualTo("tenant-1");
        assertThat(observed.get().idempotencyKey()).isEqualTo("idem-1");
        assertThat(observed.get().callerReference()).isEqualTo("checkout-1");
        assertThat(observed.get().metadata()).containsEntry("checkoutId", "checkout-1");
        assertThat(result.status()).isEqualTo(TransportPaymentPort.PaymentState.REQUIRES_CUSTOMER_ACTION);
        assertThat(result.actionUri()).isEqualTo("https://payments.example/action");
    }

    @Test
    void refundedPaymentIsNotTreatedAsFreshPaymentSuccess() {
        TransportPaymentApplicationAdapter adapter = new TransportPaymentApplicationAdapter();
        adapter.payments = new StubPayments() {
            @Override
            public Optional<PaymentObligation> find(String tenantId, String paymentId) {
                return Optional.of(payment(tenantId, paymentId, "checkout-1",
                        new BigDecimal("125000"), "IDR", PaymentStatus.REFUNDED, null));
            }
        };

        TransportPaymentPort.PaymentSnapshot result = adapter.find("tenant-1", "pay-1");

        assertThat(result.status()).isEqualTo(TransportPaymentPort.PaymentState.UNKNOWN);
    }

    private static PaymentObligation payment(String tenant, String id, String callerReference,
                                             BigDecimal amount, String currency,
                                             PaymentStatus status, String actionUri) {
        return new PaymentObligation(tenant, id, callerReference, amount, currency,
                status, null, actionUri, Instant.parse("2026-10-28T02:00:00Z"));
    }

    private static class StubPayments implements PaymentApplicationPort {
        @Override
        public PaymentObligation createOrGet(CreatePaymentObligation command) {
            return payment(command.tenantId(), "pay-default", command.callerReference(),
                    command.amount(), command.currency(), PaymentStatus.PENDING, null);
        }
        @Override
        public Optional<PaymentObligation> find(String tenantId, String paymentId) {
            return Optional.empty();
        }
        @Override
        public PaymentObligation applyVerifiedOutcome(VerifiedPaymentOutcome outcome) {
            throw new UnsupportedOperationException();
        }
        @Override
        public RefundOperation requestRefund(RequestPaymentRefund command) {
            throw new UnsupportedOperationException();
        }
    }
}
