package tech.kayys.numou.hospitality.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.hospitality.domain.StayBooking;
import tech.kayys.numou.hospitality.domain.StayBookingStatus;
import tech.kayys.numou.hospitality.domain.StayQuote;

@Testcontainers(disabledWithoutDocker = true)
class PostgresStayInventoryAdapterTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("numou_hospitality")
                    .withUsername("numou")
                    .withPassword("numou");

    private ExecutorService jdbcExecutor;
    private ExecutorService callers;
    private DataSource dataSource;
    private PostgresStayInventoryAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setURL(POSTGRES.getJdbcUrl());
        postgresDataSource.setUser(POSTGRES.getUsername());
        postgresDataSource.setPassword(POSTGRES.getPassword());
        dataSource = postgresDataSource;
        jdbcExecutor = Executors.newFixedThreadPool(8);
        callers = Executors.newFixedThreadPool(4);
        adapter = new PostgresStayInventoryAdapter(dataSource, jdbcExecutor);
        applyMigration("/db/migration/V1__hospitality_inventory_holds.sql");
        applyMigration("/db/migration/V2__index_due_hospitality_booking_holds.sql");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE hospitality_stay_booking_night, hospitality_stay_booking, hospitality_inventory_night");
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        callers.shutdownNow();
        jdbcExecutor.shutdownNow();
        assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(jdbcExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void concurrentOverlappingHoldsCannotOversellCapacityOne() throws Exception {
        String tenant = "tenant-concurrency";
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2030, 3, 10);
        LocalDate checkOut = checkIn.plusDays(2);
        adapter.provision(tenant, listing, unit, checkIn, checkOut, 1).await().indefinitely();

        StayBooking first = booking(tenant, "customer-a", listing, unit, checkIn, checkOut, "key-a");
        StayBooking second = booking(tenant, "customer-b", listing, unit, checkIn, checkOut, "key-b");

        CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(() -> attemptHold(first), callers);
        CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(() -> attemptHold(second), callers);

        assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        long persisted = List.of(first.id(), second.id()).stream()
                .filter(id -> adapter.findById(tenant, id).await().indefinitely().isPresent()).count();
        assertThat(persisted).isEqualTo(1);
    }

    @Test
    void identicalIdempotentRetryReturnsOriginalBooking() {
        String tenant = "tenant-idempotency";
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2030, 4, 10);
        LocalDate checkOut = checkIn.plusDays(1);
        adapter.provision(tenant, listing, unit, checkIn, checkOut, 1).await().indefinitely();
        StayBooking proposed = booking(tenant, "customer-a", listing, unit, checkIn, checkOut, "same-key");

        StayBooking first = adapter.createHoldAtomically(proposed, proposed.holdExpiresAt()).await().indefinitely();
        StayBooking retry = adapter.createHoldAtomically(proposed, proposed.holdExpiresAt()).await().indefinitely();

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(adapter.findById(tenant, first.id()).await().indefinitely()).contains(first);
    }

    @Test
    void bookingIdentifiersAreTenantScoped() {
        String tenantA = "tenant-a";
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2030, 5, 10);
        LocalDate checkOut = checkIn.plusDays(1);
        adapter.provision(tenantA, listing, unit, checkIn, checkOut, 1).await().indefinitely();
        StayBooking booking = booking(tenantA, "customer-a", listing, unit, checkIn, checkOut, "tenant-key");
        adapter.createHoldAtomically(booking, booking.holdExpiresAt()).await().indefinitely();

        assertThat(adapter.findById("tenant-b", booking.id()).await().indefinitely()).isEmpty();
    }

    @Test
    void confirmAndCancelTransitionsArePersistedAndReplaySafe() {
        String tenant = "tenant-lifecycle";
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2030, 6, 10);
        LocalDate checkOut = checkIn.plusDays(1);
        adapter.provision(tenant, listing, unit, checkIn, checkOut, 1).await().indefinitely();
        StayBooking booking = booking(tenant, "customer-a", listing, unit, checkIn, checkOut, "lifecycle-key");
        adapter.createHoldAtomically(booking, booking.holdExpiresAt()).await().indefinitely();

        Optional<StayBooking> confirmed = adapter.confirm(tenant, booking.id(), Instant.now()).await().indefinitely();
        Optional<StayBooking> cancelled = adapter.cancel(tenant, booking.id(), Instant.now()).await().indefinitely();
        Optional<StayBooking> replay = adapter.cancel(tenant, booking.id(), Instant.now()).await().indefinitely();

        assertThat(confirmed).get().extracting(StayBooking::status).isEqualTo(StayBookingStatus.CONFIRMED);
        assertThat(cancelled).get().extracting(StayBooking::status).isEqualTo(StayBookingStatus.CANCELLED);
        assertThat(replay).get().extracting(StayBooking::status).isEqualTo(StayBookingStatus.CANCELLED);
    }

    @Test
    void reusingIdempotencyKeyForDifferentStayIsRejected() {
        String tenant = "tenant-conflict";
        UUID listing = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        LocalDate checkIn = LocalDate.of(2030, 7, 10);
        LocalDate checkOut = checkIn.plusDays(1);
        adapter.provision(tenant, listing, unit, checkIn, checkOut.plusDays(1), 2).await().indefinitely();
        StayBooking first = booking(tenant, "customer-a", listing, unit, checkIn, checkOut, "conflict-key");
        StayBooking different = booking(tenant, "customer-a", listing, unit, checkOut, checkOut.plusDays(1), "conflict-key");
        adapter.createHoldAtomically(first, first.holdExpiresAt()).await().indefinitely();

        assertThatThrownBy(() -> adapter.createHoldAtomically(different, different.holdExpiresAt()).await().indefinitely())
                .hasRootCauseInstanceOf(PostgresStayInventoryAdapter.IdempotencyConflictException.class);
    }

    private boolean attemptHold(StayBooking booking) {
        try {
            adapter.createHoldAtomically(booking, booking.holdExpiresAt()).await().indefinitely();
            return true;
        } catch (RuntimeException failure) {
            if (hasCause(failure, PostgresStayInventoryAdapter.StayInventoryUnavailableException.class)) {
                return false;
            }
            throw failure;
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private StayBooking booking(String tenant, String customer, UUID listing, UUID unit,
                                LocalDate checkIn, LocalDate checkOut, String idempotencyKey) {
        Instant now = Instant.now();
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        StayQuote quote = new StayQuote(UUID.randomUUID(), listing, unit, checkIn, checkOut, 1, "IDR",
                Collections.nCopies((int) nights, new BigDecimal("100000")),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000").multiply(BigDecimal.valueOf(nights)),
                "policy-v1", now, now.plusSeconds(3600));
        return StayBooking.held(UUID.randomUUID(), tenant, customer, quote, idempotencyKey, now, now.plusSeconds(1800));
    }

    private void applyMigration(String resource) throws IOException, SQLException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource " + resource);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
