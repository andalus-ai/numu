package tech.kayys.numou.pos.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;
import tech.kayys.numou.pos.domain.SaleLine;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PosPostgresTestResource.class)
class PosPostgresPersistenceIT {
    private static final String FINGERPRINT = "a".repeat(64);

    @Inject
    PostgresSaleRepository sales;

    @Inject
    PostgresSaleCommandIdempotency idempotency;

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
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
