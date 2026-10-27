package tech.kayys.numou.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.events.spi.TicketBookingStore;
import tech.kayys.numou.events.spi.TicketBookingStore.BookingSnapshot;
import tech.kayys.numou.events.spi.TicketBookingStore.HoldCommand;
import tech.kayys.numou.events.spi.TicketBookingStore.Inventory;

class TicketBookingServiceTest {
    @Test
    void fingerprintIsStableAcrossRetryAndExcludesTransientHoldDeadline() {
        CapturingStore store = new CapturingStore();
        TicketBookingService service = new TicketBookingService(store,
                Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(10));
        var request = new TicketBookingService.HoldRequest("tenant-a", "actor-1", "corr-1", "key-1",
                "event-1", "regular", "WEB", 2, new BigDecimal("100.00"), "IDR");
        service.hold(request);
        service.hold(request);
        assertThat(store.commands).hasSize(2);
        assertThat(store.commands.get(0).requestFingerprint()).isEqualTo(store.commands.get(1).requestFingerprint());
        assertThat(store.commands.get(0).bookingId()).isNotEqualTo(store.commands.get(1).bookingId());
        assertThat(store.commands.get(0).expiresAt()).isEqualTo(store.commands.get(1).expiresAt());
    }

    @Test
    void validatesHoldDurationAndPositiveQuantity() {
        assertThatThrownBy(() -> new TicketBookingService(new CapturingStore(), Clock.systemUTC(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketBookingService.HoldRequest("t", "a", "c", "k", "e", "p",
                "WEB", 0, BigDecimal.ONE, "IDR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class CapturingStore implements TicketBookingStore {
        final List<HoldCommand> commands = new ArrayList<>();
        public void createInventory(Inventory inventory) { }
        public BookingSnapshot placeHold(HoldCommand command) { commands.add(command); return null; }
        public BookingSnapshot confirm(String tenantId, java.util.UUID bookingId, String paymentReference, Instant now) { return null; }
        public BookingSnapshot release(String tenantId, java.util.UUID bookingId, String reason, Instant now) { return null; }
        public int expireDueHolds(Instant now, int limit) { return 0; }
    }
}
