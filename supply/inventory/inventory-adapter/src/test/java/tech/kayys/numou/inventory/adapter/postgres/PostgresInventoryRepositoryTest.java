package tech.kayys.numou.inventory.adapter.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresInventoryRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private PostgresInventoryRepository repository;
    private ItemId item;
    private WarehouseId warehouse;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        repository = new PostgresInventoryRepository(ds, "tenant-test");
        item = ItemId.generate();
        warehouse = WarehouseId.generate();
        repository.saveMovement(movement(MovementType.RECEIPT, new BigDecimal("10"), "opening"));
    }

    @Test
    void reservation_command_replay_returns_original_and_conflicting_payload_is_rejected() {
        InventoryCommandKey key = new InventoryCommandKey("checkout", "line-1");
        InventoryReservation request = reservation(new BigDecimal("3"), "order-line-1");
        InventoryReservation created = repository.reserveIfAvailable(key, request);
        InventoryReservation replay = repository.reserveIfAvailable(key,
                reservation(new BigDecimal("3"), "order-line-1"));

        assertEquals(created.id(), replay.id());
        assertEquals(created.id(), repository.findReservationByCommandKey(key).orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> repository.reserveIfAvailable(key,
                reservation(new BigDecimal("4"), "order-line-1")));
    }

    @Test
    void concurrent_reservations_cannot_oversell_stock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> reserve = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException("start gate timed out");
                try {
                    repository.reserveIfAvailable(
                            new InventoryCommandKey("checkout", "concurrent-" + Thread.currentThread().getId()),
                            reservation(new BigDecimal("6"), "concurrent-line"));
                    return true;
                } catch (IllegalStateException insufficientStock) {
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(reserve);
            Future<Boolean> second = pool.submit(reserve);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS),
                    "exactly one 6-unit reservation should succeed against 10 units");
            assertEquals(new BigDecimal("6.00000000"), repository.findReservations(item, warehouse).stream()
                    .filter(r -> r.status() == ReservationStatus.ACTIVE)
                    .map(InventoryReservation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        } finally {
            pool.shutdownNow();
        }
    }

    private InventoryReservation reservation(BigDecimal quantity, String source) {
        return new InventoryReservation(ReservationId.generate(), "tenant-test", item, warehouse, null,
                quantity, "EA", ReservationStatus.ACTIVE, source, Instant.now(),
                Instant.now().plusSeconds(300), 1);
    }

    private InventoryMovement movement(MovementType type, BigDecimal quantity, String reference) {
        return new InventoryMovement(MovementId.generate(), item, warehouse, type, quantity,
                new BigDecimal("2.00"), "IDR", reference, Instant.now());
    }
}
