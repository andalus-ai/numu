package tech.kayys.payment.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CreatePaymentObligationTest {

    @Test
    void acceptsExplicitCurrencyAndCopiesMetadata() {
        var metadata = new HashMap<String, String>();
        metadata.put("bookingReference", "stay-123");

        var command = new CreatePaymentObligation(
                " tenant-a ", " checkout-1 ", " booking-123 ",
                new BigDecimal("125000.00"), "IDR", "Stay booking", metadata);
        metadata.put("bookingReference", "mutated");

        assertEquals("tenant-a", command.tenantId());
        assertEquals("checkout-1", command.idempotencyKey());
        assertEquals("booking-123", command.callerReference());
        assertEquals("IDR", command.currency());
        assertEquals("stay-123", command.metadata().get("bookingReference"));
        assertThrows(UnsupportedOperationException.class,
                () -> command.metadata().put("another", "value"));
    }

    @Test
    void rejectsMissingTenantOrIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentObligation(
                " ", "key", "ref", new BigDecimal("1"), "IDR", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentObligation(
                "tenant", "", "ref", new BigDecimal("1"), "IDR", null, Map.of()));
    }

    @Test
    void rejectsNonPositiveAmountAndImplicitOrInvalidCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentObligation(
                "tenant", "key", "ref", BigDecimal.ZERO, "IDR", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentObligation(
                "tenant", "key", "ref", BigDecimal.ONE, "idr", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentObligation(
                "tenant", "key", "ref", BigDecimal.ONE, "ZZZ", null, Map.of()));
    }

    @Test
    void paymentSnapshotRejectsUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentObligation(
                "tenant", "payment", "order", BigDecimal.ONE, "ZZZ",
                PaymentStatus.PENDING, null, null, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void refundSnapshotRejectsUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new RefundOperation(
                "tenant", "refund", "payment", BigDecimal.ONE, "ZZZ",
                RefundStatus.REQUESTED, Instant.parse("2026-01-01T00:00:00Z")));
    }
}