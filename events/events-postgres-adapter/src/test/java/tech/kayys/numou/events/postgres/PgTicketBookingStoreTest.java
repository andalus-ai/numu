package tech.kayys.numou.events.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.events.application.TicketBookingService;
import tech.kayys.numou.events.spi.TicketBookingException;
import tech.kayys.numou.events.spi.TicketBookingStore.Inventory;

@Testcontainers(disabledWithoutDocker = true)
class PgTicketBookingStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static PGSimpleDataSource dataSource;
    private PgTicketBookingStore store;
    private TicketBookingService service;

    @BeforeAll
    static void createSchema() throws IOException, java.sql.SQLException {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        String migration;
        try (var stream = PgTicketBookingStoreTest.class.getResourceAsStream(
                "/db/migration/V1__events_ticket_inventory_and_booking.sql")) {
            if (stream == null) throw new IllegalStateException("ticketing migration resource is missing");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String sql : migration.split(";")) if (!sql.isBlank()) statement.execute(sql);
        }
    }

    @BeforeEach
    void reset() throws java.sql.SQLException {
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("TRUNCATE event_ticket_idempotency, event_ticket_booking, event_ticket_inventory CASCADE");
        }
        store = new PgTicketBookingStore(dataSource);
        service = service(store, NOW, Duration.ofMinutes(10));
    }

    @Test
    void sameTenantKeyAndSameRequestReturnOriginalBooking() {
        inventory("tenant-a", "event-a", "general", 3);
        var request = request("tenant-a", "event-a", "general", "same-key", 2);
        var first = service.hold(request);
        var replay = service.hold(request);

        assertThat(replay.bookingId()).isEqualTo(first.bookingId());
        assertThat(replay.status()).isEqualTo("HELD");
        assertThat(replay.totalAmount()).isEqualByComparingTo("250000.00");
    }

    @Test
    void idempotencyKeyCannotBeReusedForChangedPayload() {
        inventory("tenant-a", "event-a", "general", 5);
        service.hold(request("tenant-a", "event-a", "general", "same-key", 1));

        assertThatThrownBy(() -> service.hold(request("tenant-a", "event-a", "general", "same-key", 2)))
                .isInstanceOf(TicketBookingException.class)
                .hasMessageContaining("different booking request");
    }

    @Test
    void concurrentHoldsCannotOversellCapacity() throws Exception {
        inventory("tenant-a", "event-a", "general", 1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> one = () -> attemptHold(request("tenant-a", "event-a", "general", "key-a", 1));
            Callable<Boolean> two = () -> attemptHold(request("tenant-a", "event-a", "general", "key-b", 1));
            var a = pool.submit(one);
            var b = pool.submit(two);
            assertThat((int) (a.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (int) (b.get(15, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tenantIdentityScopesInventoryAndIdempotency() {
        inventory("tenant-a", "event-a", "general", 1);
        inventory("tenant-b", "event-a", "general", 1);
        var a = service.hold(request("tenant-a", "event-a", "general", "same-key", 1));
        var b = service.hold(request("tenant-b", "event-a", "general", "same-key", 1));
        assertThat(a.bookingId()).isNotEqualTo(b.bookingId());
        assertThat(a.tenantId()).isEqualTo("tenant-a");
        assertThat(b.tenantId()).isEqualTo("tenant-b");
    }

    @Test
    void confirmationConsumesInventoryOnceAndSamePaymentReplayIsSafe() {
        inventory("tenant-a", "event-a", "general", 2);
        var held = service.hold(request("tenant-a", "event-a", "general", "key-1", 2));
        var confirmed = service.confirm("tenant-a", held.bookingId(), "verified-payment-1");
        var replay = service.confirm("tenant-a", held.bookingId(), "verified-payment-1");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(replay.status()).isEqualTo("CONFIRMED");
        assertThat(replay.paymentReference()).isEqualTo("verified-payment-1");
        assertThatThrownBy(() -> service.hold(request("tenant-a", "event-a", "general", "key-2", 1)))
                .isInstanceOf(TicketBookingException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void latePaymentCannotConfirmExpiredHold() {
        inventory("tenant-a", "event-a", "general", 2);
        var held = service.hold(request("tenant-a", "event-a", "general", "key-1", 1));

        assertThatThrownBy(() -> store.confirm("tenant-a", held.bookingId(), "verified-payment-late",
                held.expiresAt()))
                .isInstanceOf(TicketBookingException.class)
                .hasMessageContaining("expired");
        assertThat(service.hold(request("tenant-a", "event-a", "general", "key-1", 1)).status())
                .isEqualTo("EXPIRED");
    }

    private boolean attemptHold(TicketBookingService.HoldRequest request) {
        try {
            service.hold(request);
            return true;
        } catch (TicketBookingException expected) {
            if (expected.code() != TicketBookingException.Code.INSUFFICIENT_INVENTORY) throw expected;
            return false;
        }
    }

    private void inventory(String tenant, String event, String product, int capacity) {
        service.createInventory(new Inventory(tenant, event, product, "IDR",
                new BigDecimal("125000.00"), capacity, Math.min(4, capacity)));
    }

    private static TicketBookingService service(PgTicketBookingStore store, Instant now, Duration ttl) {
        return new TicketBookingService(store, Clock.fixed(now, ZoneOffset.UTC), ttl);
    }

    private static TicketBookingService.HoldRequest request(
            String tenant, String event, String product, String key, int quantity) {
        return new TicketBookingService.HoldRequest(tenant, "actor-1", "correlation-1", key,
                event, product, "WEB", quantity, new BigDecimal("125000.00"), "IDR");
    }
}
