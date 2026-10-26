package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.StayBooking;
import tech.kayys.numou.hospitality.domain.StayBookingStatus;
import tech.kayys.numou.hospitality.domain.StayQuote;

class StayBookingTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    private StayQuote quote() {
        return new StayQuote(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2, "IDR",
                List.of(new BigDecimal("500000"), new BigDecimal("500000")),
                new BigDecimal("25000"), new BigDecimal("25000"), new BigDecimal("1050000"),
                "cancel-v1", NOW, NOW.plusSeconds(900));
    }

    @Test
    void createsHeldBookingAndConfirmsBeforeExpiry() {
        StayQuote quote = quote();
        StayBooking booking = StayBooking.held(UUID.randomUUID(), "tenant-a", "customer-a",
                quote, "request-1", NOW, NOW.plusSeconds(600));

        assertThat(booking.status()).isEqualTo(StayBookingStatus.HELD);
        assertThat(booking.matches(quote, "customer-a")).isTrue();

        booking.confirm(NOW.plusSeconds(100));
        assertThat(booking.status()).isEqualTo(StayBookingStatus.CONFIRMED);
    }

    @Test
    void rejectsExpiredQuoteAndExpiresUnconfirmedHold() {
        StayQuote quote = quote();
        assertThatThrownBy(() -> StayBooking.held(UUID.randomUUID(), "tenant-a", "customer-a",
                quote, "request-1", NOW.plusSeconds(900), NOW.plusSeconds(1200)))
                .isInstanceOf(IllegalArgumentException.class);

        StayBooking booking = StayBooking.held(UUID.randomUUID(), "tenant-a", "customer-a",
                quote, "request-2", NOW, NOW.plusSeconds(100));
        booking.expire(NOW.plusSeconds(100));
        assertThat(booking.status()).isEqualTo(StayBookingStatus.EXPIRED);
        assertThatThrownBy(() -> booking.confirm(NOW.plusSeconds(101)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void idempotentCancellationAndTerminalStateGuard() {
        StayBooking booking = StayBooking.held(UUID.randomUUID(), "tenant-a", "customer-a",
                quote(), "request-3", NOW, NOW.plusSeconds(100));
        booking.cancel();
        booking.cancel();
        assertThat(booking.status()).isEqualTo(StayBookingStatus.CANCELLED);
        assertThatThrownBy(() -> booking.confirm(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
