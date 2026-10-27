package tech.kayys.payment.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void fingerprintIgnoresIdempotencyKeyMetadataOrderAndDecimalScale() {
        var first = new CreatePaymentObligation(
                "tenant", "key-1", "booking-1", new BigDecimal("100.00"),
                "IDR", "booking", Map.of("b", "2", "a", "1"));
        var retry = new CreatePaymentObligation(
                "tenant", "key-2", "booking-1", new BigDecimal("100"),
                "IDR", "booking", Map.of("a", "1", "b", "2"));

        assertEquals(PaymentRequestFingerprint.sha256(first),
                PaymentRequestFingerprint.sha256(retry));
    }

    @Test
    void fingerprintChangesWhenSemanticPaymentFieldsChange() {
        var original = new CreatePaymentObligation(
                "tenant", "key", "booking-1", new BigDecimal("100"),
                "IDR", "booking", Map.of("channel", "web"));
        var differentAmount = new CreatePaymentObligation(
                "tenant", "key", "booking-1", new BigDecimal("101"),
                "IDR", "booking", Map.of("channel", "web"));
        var differentTenant = new CreatePaymentObligation(
                "tenant-2", "key", "booking-1", new BigDecimal("100"),
                "IDR", "booking", Map.of("channel", "web"));
        var differentMetadata = new CreatePaymentObligation(
                "tenant", "key", "booking-1", new BigDecimal("100"),
                "IDR", "booking", Map.of("channel", "mobile"));

        var fingerprint = PaymentRequestFingerprint.sha256(original);
        assertNotEquals(fingerprint, PaymentRequestFingerprint.sha256(differentAmount));
        assertNotEquals(fingerprint, PaymentRequestFingerprint.sha256(differentTenant));
        assertNotEquals(fingerprint, PaymentRequestFingerprint.sha256(differentMetadata));
        assertEquals(64, fingerprint.length());
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