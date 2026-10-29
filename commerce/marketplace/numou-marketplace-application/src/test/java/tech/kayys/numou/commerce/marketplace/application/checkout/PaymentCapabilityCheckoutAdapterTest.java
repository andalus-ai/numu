package tech.kayys.numou.commerce.marketplace.application.checkout;

import org.junit.jupiter.api.Test;
import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentStatus;
import tech.kayys.payment.api.RefundOperation;
import tech.kayys.payment.api.RequestPaymentRefund;
import tech.kayys.payment.api.VerifiedPaymentOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCapabilityCheckoutAdapterTest {
    @Test
    void delegatesIdempotentCreateAndMapsPendingState() {
        Instant now = Instant.parse("2026-10-28T09:00:00Z");
        AtomicReference<CreatePaymentObligation> captured = new AtomicReference<>();
        PaymentApplicationPort paymentPort = new StubPaymentPort(captured, obligation("pay-1", PaymentStatus.PENDING, now));

        var adapter = new PaymentCapabilityCheckoutAdapter(paymentPort);
        var result = adapter.createOrGet("tenant-a", "checkout:abc:payment", "order-1",
                new BigDecimal("125.00"), "IDR");

        assertEquals("pay-1", result.paymentId());
        assertEquals(MarketplaceCheckoutPaymentPort.PaymentState.PENDING, result.state());
        assertEquals("checkout:abc:payment", captured.get().idempotencyKey());
        assertEquals("order-1", captured.get().callerReference());
        assertEquals(new BigDecimal("125.00"), captured.get().amount());
    }

    @Test
    void mapsRefundedPaymentToUnknownRatherThanFalseCheckoutFailure() {
        Instant now = Instant.parse("2026-10-28T09:00:00Z");
        PaymentApplicationPort paymentPort = new StubPaymentPort(new AtomicReference<>(),
                obligation("pay-2", PaymentStatus.REFUNDED, now));

        var result = new PaymentCapabilityCheckoutAdapter(paymentPort).find("tenant-a", "pay-2").orElseThrow();

        assertEquals(MarketplaceCheckoutPaymentPort.PaymentState.UNKNOWN, result.state());
    }

    @Test
    void translatesAuthoritativePaymentRejectionIntoKnownFailure() {
        PaymentApplicationPort rejecting = new StubPaymentPort(new AtomicReference<>(), null) {
            @Override
            public PaymentObligation createOrGet(CreatePaymentObligation command) {
                throw new PaymentApplicationPort.PaymentOperationRejectedException("policy rejected");
            }
        };
        var adapter = new PaymentCapabilityCheckoutAdapter(rejecting);

        assertThrows(MarketplaceCheckoutPaymentPort.PaymentRejectedException.class, () ->
                adapter.createOrGet("tenant-a", "key", "order-1", new BigDecimal("12.00"), "IDR"));
    }

    @Test
    void rejectsZeroValuePaymentBeforeCallingPaymentCapability() {
        PaymentApplicationPort paymentPort = new StubPaymentPort(new AtomicReference<>(), null);
        var adapter = new PaymentCapabilityCheckoutAdapter(paymentPort);
        assertThrows(IllegalArgumentException.class, () -> adapter.createOrGet(
                "tenant-a", "key", "order-1", BigDecimal.ZERO, "IDR"));
    }

    private static PaymentObligation obligation(String id, PaymentStatus status, Instant now) {
        return new PaymentObligation("tenant-a", id, "order-1", new BigDecimal("125.00"),
                "IDR", status, null, null, now);
    }

    private static class StubPaymentPort implements PaymentApplicationPort {
        private final AtomicReference<CreatePaymentObligation> captured;
        private final PaymentObligation response;

        private StubPaymentPort(AtomicReference<CreatePaymentObligation> captured, PaymentObligation response) {
            this.captured = captured;
            this.response = response;
        }

        @Override
        public PaymentObligation createOrGet(CreatePaymentObligation command) {
            captured.set(command);
            return response;
        }

        @Override
        public Optional<PaymentObligation> find(String tenantId, String paymentId) {
            return response != null && response.tenantId().equals(tenantId)
                    && response.paymentId().equals(paymentId) ? Optional.of(response) : Optional.empty();
        }

        @Override
        public RefundOperation requestRefund(RequestPaymentRefund command) {
            throw new UnsupportedOperationException();
        }
    }
}
