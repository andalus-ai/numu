package tech.kayys.numou.transport.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.transport.domain.DepartureInventory;
import tech.kayys.numou.transport.domain.RouteStatus;
import tech.kayys.numou.transport.domain.RouteStop;
import tech.kayys.numou.transport.domain.ScheduledDeparture;
import tech.kayys.numou.transport.domain.ScheduledStopTime;
import tech.kayys.numou.transport.domain.TransportRoute;
import tech.kayys.numou.transport.spi.InventoryConcurrencyException;

import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PgDepartureInventoryRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-27T02:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(300);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static PgSimpleDataSource dataSource;
    private PgDepartureInventoryRepository repository;

    @BeforeAll
    static void createSchema() throws IOException, java.sql.SQLException {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        String migration;
        try (var stream = PgDepartureInventoryRepositoryTest.class.getResourceAsStream(
                "/db/migration/V1__transport_departure_inventory.sql")) {
            if (stream == null) throw new IllegalStateException("inventory migration resource is missing");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    @BeforeEach
    void resetRows() throws java.sql.SQLException {
        repository = new PgDepartureInventoryRepository(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE transport_passenger_hold, transport_departure_inventory");
        }
    }

    @Test
    void persistsAndReconstitutesHoldsAndLifecycleState() {
        DepartureInventory initial = inventory("tenant-a", UUID.randomUUID(), 2);
        repository.create(initial);

        DepartureInventory loaded = repository.find("tenant-a", initial.departureId()).orElseThrow();
        DepartureInventory held = loaded.hold(UUID.randomUUID(), "booking-1", "request-1",
                1, 3, 1, EXPIRES, NOW).inventory();
        repository.save(held, loaded.version());

        DepartureInventory persisted = repository.find("tenant-a", initial.departureId()).orElseThrow();
        assertThat(persisted.holds()).hasSize(1);
        assertThat(persisted.remainingCapacity(1, 3, NOW)).isEqualTo(1);

        DepartureInventory confirmed = persisted.confirm(persisted.holds().get(0).id(), NOW.plusSeconds(1));
        repository.save(confirmed, persisted.version());

        DepartureInventory finalState = repository.find("tenant-a", initial.departureId()).orElseThrow();
        assertThat(finalState.holds().get(0).status().name()).isEqualTo("CONFIRMED");
        assertThat(finalState.remainingCapacity(1, 3, NOW.plusSeconds(400))).isEqualTo(1);
    }

    @Test
    void tenantScopeIsPartOfInventoryAndHoldIdentity() {
        UUID sharedDepartureId = UUID.randomUUID();
        DepartureInventory tenantA = inventory("tenant-a", sharedDepartureId, 1);
        DepartureInventory tenantB = inventory("tenant-b", sharedDepartureId, 1);
        repository.create(tenantA);
        repository.create(tenantB);

        DepartureInventory held = repository.find("tenant-a", sharedDepartureId).orElseThrow()
                .hold(UUID.randomUUID(), "same-key", "same-fingerprint", 1, 3, 1, EXPIRES, NOW).inventory();
        repository.save(held, 0);

        assertThat(repository.find("tenant-a", sharedDepartureId).orElseThrow().holds()).hasSize(1);
        assertThat(repository.find("tenant-b", sharedDepartureId).orElseThrow().holds()).isEmpty();
    }

    @Test
    void rejectsStaleSnapshotAndPreventsConcurrentOversell() throws Exception {
        DepartureInventory initial = inventory("tenant-a", UUID.randomUUID(), 1);
        repository.create(initial);
        DepartureInventory snapshotA = repository.find("tenant-a", initial.departureId()).orElseThrow();
        DepartureInventory snapshotB = repository.find("tenant-a", initial.departureId()).orElseThrow();

        DepartureInventory candidateA = snapshotA.hold(UUID.randomUUID(), "booking-a", "fingerprint-a",
                1, 3, 1, EXPIRES, NOW).inventory();
        DepartureInventory candidateB = snapshotB.hold(UUID.randomUUID(), "booking-b", "fingerprint-b",
                1, 3, 1, EXPIRES, NOW).inventory();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> writeA = executor.submit(() -> saveAfterGate(candidateA, snapshotA.version(), ready, start));
            Future<Boolean> writeB = executor.submit(() -> saveAfterGate(candidateB, snapshotB.version(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean resultA = writeA.get(10, TimeUnit.SECONDS);
            boolean resultB = writeB.get(10, TimeUnit.SECONDS);

            assertThat(resultA ^ resultB).isTrue();
            DepartureInventory persisted = repository.find("tenant-a", initial.departureId()).orElseThrow();
            assertThat(persisted.holds()).hasSize(1);
            assertThat(persisted.remainingCapacity(1, 3, NOW)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackVersionUpdateWhenHoldInsertFails() throws java.sql.SQLException {
        DepartureInventory initial = inventory("tenant-a", UUID.randomUUID(), 2);
        repository.create(initial);
        DepartureInventory current = repository.find("tenant-a", initial.departureId()).orElseThrow();

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE FUNCTION fail_transport_hold_insert() RETURNS trigger AS $$
                    BEGIN
                        IF NEW.idempotency_key = 'force-rollback' THEN
                            RAISE EXCEPTION 'forced inventory hold insert failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            statement.execute("""
                    CREATE TRIGGER transport_test_fail_hold_insert
                    BEFORE INSERT ON transport_passenger_hold
                    FOR EACH ROW EXECUTE FUNCTION fail_transport_hold_insert()
                    """);
        }

        try {
            DepartureInventory candidate = current.hold(UUID.randomUUID(), "force-rollback", "fp-rollback",
                    1, 3, 1, EXPIRES, NOW).inventory();
            assertThatThrownBy(() -> repository.save(candidate, current.version()))
                    .isInstanceOf(IllegalStateException.class);

            DepartureInventory afterRollback = repository.find("tenant-a", initial.departureId()).orElseThrow();
            assertThat(afterRollback.version()).isEqualTo(current.version());
            assertThat(afterRollback.holds()).isEmpty();
        } finally {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS transport_test_fail_hold_insert ON transport_passenger_hold");
                statement.execute("DROP FUNCTION IF EXISTS fail_transport_hold_insert()");
            }
        }
    }

    @Test
    void refusesSnapshotsThatOmitPersistedHoldHistory() {
        DepartureInventory initial = inventory("tenant-a", UUID.randomUUID(), 2);
        repository.create(initial);
        DepartureInventory loaded = repository.find("tenant-a", initial.departureId()).orElseThrow();
        DepartureInventory withHold = loaded.hold(UUID.randomUUID(), "booking-1", "fp-1",
                1, 2, 1, EXPIRES, NOW).inventory();
        repository.save(withHold, loaded.version());

        DepartureInventory current = repository.find("tenant-a", initial.departureId()).orElseThrow();
        DepartureInventory missingHistory = DepartureInventory.reconstitute(current.tenantId(),
                current.departureId(), current.capacity(), current.stopCount(), current.version() + 1, List.of());

        assertThatThrownBy(() -> repository.save(missingHistory, current.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot omit persisted hold history");
        DepartureInventory afterAttempt = repository.find("tenant-a", initial.departureId()).orElseThrow();
        assertThat(afterAttempt.version()).isEqualTo(current.version());
        assertThat(afterAttempt.holds()).hasSize(1);
    }

    private boolean saveAfterGate(DepartureInventory candidate, int expectedVersion,
                                  CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency test gate timed out");
        try {
            repository.save(candidate, expectedVersion);
            return true;
        } catch (InventoryConcurrencyException expected) {
            return false;
        }
    }

    private DepartureInventory inventory(String tenantId, UUID departureId, int capacity) {
        UUID firstStop = UUID.randomUUID();
        UUID middleStop = UUID.randomUUID();
        UUID finalStop = UUID.randomUUID();
        TransportRoute route = new TransportRoute(UUID.randomUUID(), tenantId, "T-1", "Test route",
                List.of(
                        new RouteStop(firstStop, 1, true, false),
                        new RouteStop(middleStop, 2, true, true),
                        new RouteStop(finalStop, 3, false, true)),
                RouteStatus.PUBLISHED);
        ScheduledDeparture departure = ScheduledDeparture.draft(departureId, route, "T-1",
                List.of(
                        new ScheduledStopTime(firstStop, 1, null, NOW.plusSeconds(60)),
                        new ScheduledStopTime(middleStop, 2, NOW.plusSeconds(120), NOW.plusSeconds(130)),
                        new ScheduledStopTime(finalStop, 3, NOW.plusSeconds(180), null)),
                capacity).publish();
        return DepartureInventory.open(departure);
    }
}
