package tech.kayys.numou.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.events.domain.TicketingDomain.AdmissionDecision;
import tech.kayys.numou.events.domain.TicketingDomain.AdmissionTicket;
import tech.kayys.numou.events.domain.TicketingDomain.Booking;
import tech.kayys.numou.events.domain.TicketingDomain.BookingStatus;
import tech.kayys.numou.events.domain.TicketingDomain.Credential;
import tech.kayys.numou.events.domain.TicketingDomain.CredentialType;
import tech.kayys.numou.events.domain.TicketingDomain.SalesChannel;
import tech.kayys.numou.events.domain.TicketingDomain.TicketProduct;
import tech.kayys.numou.events.domain.TicketingDomain.TicketStatus;

class TicketingDomainTest {
    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T14:00:00Z");

    @Test
    void inventoryCannotOversellAndHonorsSalesChannelNeutralLimits() {
        TicketProduct product = product(3, 3);
        product.hold(2, START);
        assertThat(product.available()).isEqualTo(1);
        assertThatThrownBy(() -> product.hold(2, START))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient");
        product.confirmHold(2);
        assertThat(product.sold()).isEqualTo(2);
        assertThat(product.available()).isEqualTo(1);
    }

    @Test
    void inventoryRejectsSalesOutsideWindowAndExcessOrderQuantity() {
        TicketProduct product = product(10, 2);
        assertThatThrownBy(() -> product.hold(1, START.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> product.hold(3, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bookingChannelDoesNotChangePaymentOrExpiryRules() {
        Booking booking = booking(SalesChannel.PARTNER_API, END);
        booking.beginPayment(START);
        booking.confirmPayment("trusted-payment-123", START.plusSeconds(30));
        booking.confirmPayment("trusted-payment-123", START.plusSeconds(31));
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.paymentReference()).isEqualTo("trusted-payment-123");
    }

    @Test
    void expiredBookingCannotBeConfirmedByLatePayment() {
        Booking booking = booking(SalesChannel.MOBILE_APP, START.plusSeconds(10));
        booking.beginPayment(START);
        assertThatThrownBy(() -> booking.confirmPayment("payment-late", START.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void sameTicketSupportsQrAndRfidButOnlyOneAdmission() {
        Credential qr = new Credential(CredentialType.QR_CODE, "a".repeat(64), 1);
        Credential rfid = new Credential(CredentialType.RFID, "b".repeat(64), 1);
        AdmissionTicket ticket = new AdmissionTicket("tenant-a", "event-1", "ticket-1",
                START, END, Set.of(qr, rfid));

        assertThat(ticket.admit("event-1", CredentialType.RFID, "b".repeat(64), "scan-1", START))
                .isEqualTo(AdmissionDecision.ADMITTED);
        assertThat(ticket.admit("event-1", CredentialType.QR_CODE, "a".repeat(64), "scan-2", START))
                .isEqualTo(AdmissionDecision.ALREADY_ADMITTED);
        assertThat(ticket.status()).isEqualTo(TicketStatus.USED);
    }

    @Test
    void scanRetryIsIdempotentAndWrongEventDoesNotConsumeTicket() {
        Credential barcode = new Credential(CredentialType.BARCODE, "c".repeat(64), 1);
        AdmissionTicket ticket = new AdmissionTicket("tenant-a", "event-1", "ticket-1",
                START, END, Set.of(barcode));

        assertThat(ticket.admit("event-other", CredentialType.BARCODE, "c".repeat(64), "scan-1", START))
                .isEqualTo(AdmissionDecision.WRONG_EVENT);
        assertThat(ticket.admit("event-1", CredentialType.BARCODE, "c".repeat(64), "scan-1", START))
                .isEqualTo(AdmissionDecision.WRONG_EVENT);
        assertThat(ticket.admit("event-1", CredentialType.BARCODE, "c".repeat(64), "scan-2", START))
                .isEqualTo(AdmissionDecision.ADMITTED);
    }

    @Test
    void invalidCredentialDoesNotConsumeTicketAndRevocationBlocksAdmission() {
        Credential qr = new Credential(CredentialType.QR_CODE, "d".repeat(64), 1);
        AdmissionTicket ticket = new AdmissionTicket("tenant-a", "event-1", "ticket-1",
                START, END, Set.of(qr));
        assertThat(ticket.admit("event-1", CredentialType.QR_CODE, "wrong", "scan-1", START))
                .isEqualTo(AdmissionDecision.INVALID_CREDENTIAL);
        ticket.revoke();
        assertThat(ticket.admit("event-1", CredentialType.QR_CODE, "d".repeat(64), "scan-2", START))
                .isEqualTo(AdmissionDecision.REVOKED);
    }

    @Test
    void validatesCurrencyAndCredentialConfiguration() {
        assertThatThrownBy(() -> new TicketProduct("tenant", "event", "product", "IDR",
                new BigDecimal("1.12345"), 5, 2, START, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdmissionTicket("tenant", "event", "ticket", START, END, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TicketProduct product(int capacity, int maxPerOrder) {
        return new TicketProduct("tenant-a", "event-1", "regular", "IDR",
                new BigDecimal("125000.00"), capacity, maxPerOrder, START, END);
    }

    private static Booking booking(SalesChannel channel, Instant expiresAt) {
        return new Booking("tenant-a", "booking-1", "event-1", "regular", channel,
                "request-key-1", 2, new BigDecimal("250000.00"), "IDR", expiresAt);
    }
}
