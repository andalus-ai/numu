package tech.kayys.numou.pos.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;
import tech.kayys.numou.pos.application.port.out.PosOutboxWriter;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;
import tech.kayys.numou.pos.domain.SaleLine;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PosPostgresTestResource.class)
class PosPostgresPersistenceTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Inject
    PostgresSaleRepository sales;

    @Inject
    PostgresSaleCommandIdempotency idempotency;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    PosOutboxWriter outbox;

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM pos_outbox_event");
            statement.executeUpdate("DELETE FROM pos_command_idempotency");
            statement.executeUpdate("DELETE FROM pos_sale");
        }
    }

    @Test
    void persistsAndRoundTripsSaleIncludingSharedMoneyAndEnforcesTenantScope() {
        UUID tenant = UUID.randomUUID();
        Sale sale = sale(tenant, UUID.randomUUID()).addLine(new SaleLine(
                UUID.randomUUID(), "item-1", "Coffee", "SKU-1",
                new BigDecimal("1.5"), "cup", Money.of(new BigDecimal("1250.50"), "IDR")));

        VersionedSale created = sales.create(sale);
        VersionedSale loaded = sales.find(tenant, sale.id()).orElseThrow();

        assertEquals(0, created.version());
        assertEquals(sale, loaded.sale());
        assertEquals(new BigDecimal("1250.50"), loaded.sale().lines().getFirst().unitPrice().amount());
        assertEquals("IDR", loaded.sale().lines().getFirst().unitPrice().currency().code());
        assertTrue(sales.find(UUID.randomUUID(), sale.id()).isEmpty(),
                "a sale must not be visible through another tenant identifier");
    }

    @Test
    void optimisticVersionRejectsStaleUpdate() {
        UUID tenant = UUID.randomUUID();
        Sale sale = sale(tenant, UUID.randomUUID());
        sales.create(sale);

        VersionedSale updated = sales.update(sale.voidSale(), 0);
        assertEquals(1, updated.version());
        assertThrows(PostgresSaleRepository.OptimisticSaleConflictException.class,
                () -> sales.update(sale, 0));
        assertEquals(Sale.Status.VOIDED, sales.find(tenant, sale.id()).orElseThrow().sale().status());
    }

    @Test
    void concurrentSameKeyCommandsExecuteTheBusinessEffectOnceAndReplayTheSavedResult() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Sale sale = sale(tenant, UUID.randomUUID());
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<VersionedSale> first = CompletableFuture.supplyAsync(
                    () -> idempotency.execute(tenant, actor, "checkout-concurrent", "OPEN_SALE",
                            FINGERPRINT, () -> {
                                executions.incrementAndGet();
                                return sales.create(sale);
                            }), executor);
            CompletableFuture<VersionedSale> second = CompletableFuture.supplyAsync(
                    () -> idempotency.execute(tenant, actor, "checkout-concurrent", "OPEN_SALE",
                            FINGERPRINT, () -> {
                                executions.incrementAndGet();
                                return sales.create(sale);
                            }), executor);

            VersionedSale firstResult = first.join();
            VersionedSale secondResult = second.join();
            assertEquals(1, executions.get());
            assertEquals(firstResult, secondResult);
            assertEquals(1, countSales(tenant, sale.id()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void changedRequestFingerprintIsRejectedAndFailedCommandRollsBackSaleAndReservation() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Sale sale = sale(tenant, UUID.randomUUID());
        VersionedSale first = idempotency.execute(tenant, actor, "checkout-conflict", "OPEN_SALE",
                FINGERPRINT, () -> sales.create(sale));
        assertEquals(0, first.version());
        assertThrows(PostgresSaleCommandIdempotency.IdempotencyConflictException.class,
                () -> idempotency.execute(tenant, actor, "checkout-conflict", "OPEN_SALE",
                        "b".repeat(64), () -> sales.create(sale)));

        UUID rollbackSaleId = UUID.randomUUID();
        Sale rollbackSale = sale(tenant, rollbackSaleId);
        assertThrows(IllegalStateException.class,
                () -> idempotency.execute(tenant, actor, "checkout-rollback", "OPEN_SALE",
                        "c".repeat(64), () -> {
                            sales.create(rollbackSale);
                            throw new IllegalStateException("simulate checkout failure");
                        }));
        assertTrue(sales.find(tenant, rollbackSaleId).isEmpty(),
                "the sale mutation must roll back with the idempotency reservation");
        assertEquals(0, countIdempotencyRows(tenant, actor, "checkout-rollback"),
                "a failed command must not leave a committed PROCESSING reservation");
    }

    @Test
    void appendsDurablePendingEventWithTenantAndSchemaMetadata() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        outbox.append(new PosOutboxEvent(eventId, tenant, "Sale", saleId.toString(),
                "pos.sale.opened", 1, Instant.parse("2026-10-29T08:00:00Z"),
                "{\"saleId\":\"" + saleId + "\",\"channel\":\"POS\"}"));

        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT tenant_id, aggregate_type, aggregate_id, event_type, schema_version, payload::text, delivery_status "
                             + "FROM pos_outbox_event WHERE event_id = ?")) {
            statement.setObject(1, eventId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(tenant, result.getObject("tenant_id", UUID.class));
                assertEquals("Sale", result.getString("aggregate_type"));
                assertEquals(saleId.toString(), result.getString("aggregate_id"));
                assertEquals("pos.sale.opened", result.getString("event_type"));
                assertEquals(1, result.getInt("schema_version"));
                assertTrue(result.getString("payload").contains(saleId.toString()));
                assertEquals("PENDING", result.getString("delivery_status"));
            }
        }
    }

    @Test
    void outboxAndSaleWritesRollbackTogetherWhenIdempotentCommandFails() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Sale sale = sale(tenant, saleId);

        assertThrows(IllegalStateException.class,
                () -> idempotency.execute(tenant, actor, "checkout-outbox-rollback", "OPEN_SALE",
                        "d".repeat(64), () -> {
                            sales.create(sale);
                            outbox.append(new PosOutboxEvent(eventId, tenant, "Sale", saleId.toString(),
                                    "pos.sale.opened", 1, Instant.parse("2026-10-29T08:00:00Z"),
                                    "{\"saleId\":\"" + saleId + "\"}"));
                            throw new IllegalStateException("simulate failure after sale and outbox writes");
                        }));

        assertTrue(sales.find(tenant, saleId).isEmpty());
        assertEquals(0, countOutboxEvents(eventId));
        assertEquals(0, countIdempotencyRows(tenant, actor, "checkout-outbox-rollback"));
    }


    @Test
    void saleCommandServiceCompletionRollsBackSaleOutboxAndIdempotencyWhenAppendFails() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID registerId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        String idempotencyKey = "service-complete-rollback-" + saleId;
        Instant now = Instant.parse("2026-10-29T08:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        tech.kayys.numou.pos.application.port.out.OpenShiftAccess shifts =
                (ignoredTenant, ignoredBranch, ignoredRegister, ignoredShift, ignoredActor) -> { };
        tech.kayys.numou.pos.application.SaleCommandContext context =
                new tech.kayys.numou.pos.application.SaleCommandContext(tenant, actor, "rollback-test");

        tech.kayys.numou.pos.application.SaleCommandService setup =
                new tech.kayys.numou.pos.application.SaleCommandService(sales, shifts, idempotency, outbox, clock);
        setup.open(context, "open-" + saleId, saleId, branchId, registerId, shiftId, SaleChannel.POS, "IDR");
        setup.addLine(context, "line-" + saleId, saleId, 0,
                new SaleLine(UUID.randomUUID(), "item-1", "Coffee", "SKU-1",
                        BigDecimal.ONE, "cup", Money.of(new BigDecimal("25000"), "IDR")));

        PosOutboxWriter appendThenFail = event -> {
            outbox.append(event);
            throw new IllegalStateException("simulate failure after completed event append");
        };
        tech.kayys.numou.pos.application.SaleCommandService failing =
                new tech.kayys.numou.pos.application.SaleCommandService(
                        sales, shifts, idempotency, appendThenFail, clock);

        assertThrows(IllegalStateException.class,
                () -> failing.complete(context, idempotencyKey, saleId, 1));

        VersionedSale persisted = sales.find(tenant, saleId).orElseThrow();
        assertEquals(Sale.Status.OPEN, persisted.sale().status(),
                "sale completion must roll back when event append fails");
        assertEquals(1, persisted.version(), "the prior line addition must remain committed");
        assertEquals(0, countOutboxEvents(tenant, saleId, "pos.sale.completed"),
                "the completed event must roll back with the sale transition");
        assertEquals(0, countIdempotencyRows(tenant, actor, idempotencyKey),
                "a failed command must not leave a completed or PROCESSING idempotency row");
    }

    private int countOutboxEvents(UUID tenantId, UUID aggregateId, String eventType) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM pos_outbox_event WHERE tenant_id = ? AND aggregate_id = ? AND event_type = ?")) {
            statement.setObject(1, tenantId);
            statement.setString(2, aggregateId.toString());
            statement.setString(3, eventType);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int countOutboxEvents(UUID eventId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM pos_outbox_event WHERE event_id = ?")) {
            statement.setObject(1, eventId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Sale sale(UUID tenantId, UUID saleId) {
        return Sale.open(saleId, tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SaleChannel.POS, "IDR", Instant.parse("2026-10-29T08:00:00Z"));
    }

    private int countSales(UUID tenantId, UUID saleId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM pos_sale WHERE tenant_id = ? AND sale_id = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, saleId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int countIdempotencyRows(UUID tenantId, UUID actorId, String key) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM pos_command_idempotency WHERE tenant_id = ? AND actor_id = ? AND idempotency_key = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, actorId);
            statement.setString(3, key);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
