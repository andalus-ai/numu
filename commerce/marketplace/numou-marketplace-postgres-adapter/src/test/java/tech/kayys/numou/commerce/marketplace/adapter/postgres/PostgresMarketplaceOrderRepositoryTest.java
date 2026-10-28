package tech.kayys.numou.commerce.marketplace.adapter.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;
import tech.kayys.numou.commerce.marketplace.application.MarketplaceOrderRequestFingerprint;
import tech.kayys.numou.commerce.marketplace.application.MarketplaceOrderIdempotencyConflictException;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMarketplaceOrderRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_marketplace")
            .withUsername("numou")
            .withPassword("numou");

    private PostgresMarketplaceOrderRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        repository = new PostgresMarketplaceOrderRepository(dataSource);
        try (InputStream migration = getClass().getResourceAsStream("/db/migration/V1__marketplace_orders.sql")) {
            assertNotNull(migration, "marketplace migration must be packaged");
            String sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS marketplace_order_idempotency CASCADE");
                statement.execute("DROP TABLE IF EXISTS marketplace_order_line CASCADE");
                statement.execute("DROP TABLE IF EXISTS marketplace_order CASCADE");
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
                try (InputStream secondMigration = getClass().getResourceAsStream(
                        "/db/migration/V2__marketplace_order_idempotency.sql")) {
                    assertNotNull(secondMigration, "idempotency migration must be packaged");
                    String secondSql = new String(secondMigration.readAllBytes(), StandardCharsets.UTF_8);
                    for (String command : secondSql.split(";")) {
                        if (!command.isBlank()) statement.execute(command);
                    }
                }
            }
        }
    }

    @Test
    void persistsAndReconstitutesOrderAndImmutableCommercialLines() {
        MarketplaceOrder order = order();
        repository.create(order);

        MarketplaceOrder loaded = repository.findByTenantAndId("tenant-a", order.id()).orElseThrow();
        assertEquals(order.id(), loaded.id());
        assertEquals(order.tenantId(), loaded.tenantId());
        assertEquals(order.buyerId(), loaded.buyerId());
        assertEquals(order.lines(), loaded.lines());
        assertEquals(MarketplaceOrderStatus.CREATED, loaded.status());
        assertTrue(repository.findByTenantAndId("tenant-b", order.id()).isEmpty());
    }

    @Test
    void idempotentCreationReturnsTheOriginalOrderAndRejectsChangedRequest() {
        MarketplaceOrder proposed = order();
        String fingerprint = MarketplaceOrderRequestFingerprint.calculate(
                proposed.tenantId(), proposed.buyerId(), proposed.currency(), proposed.lines());

        MarketplaceOrder first = repository.createOrGet("tenant-a", "checkout-1", fingerprint, proposed);
        MarketplaceOrder retry = repository.createOrGet("tenant-a", "checkout-1", fingerprint, order());

        assertEquals(first.id(), retry.id());
        assertEquals(1, countOrders());
        assertThrows(MarketplaceOrderIdempotencyConflictException.class, () ->
                repository.createOrGet("tenant-a", "checkout-1", "0".repeat(64), order()));
    }

    private int countOrders() {
        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM marketplace_order")) {
            result.next();
            return result.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void compareAndSetOnlyAllowsOneConcurrentTransitionFromExpectedStatus() throws Exception {
        MarketplaceOrder order = order();
        repository.create(order);
        MarketplaceOrder submitted = order.submit(Instant.parse("2026-10-28T01:00:00Z"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> repository.replaceIfCurrentStatus(
                    "tenant-a", order.id(), MarketplaceOrderStatus.CREATED, submitted);
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            boolean firstWon = first.get(10, TimeUnit.SECONDS);
            boolean secondWon = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon, "exactly one concurrent compare-and-set should win");
            assertEquals(MarketplaceOrderStatus.SUBMITTED,
                    repository.findByTenantAndId("tenant-a", order.id()).orElseThrow().status());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectsReplacementWithDifferentTenantOrChangedLines() {
        MarketplaceOrder order = order();
        repository.create(order);
        MarketplaceOrder submitted = order.submit(Instant.parse("2026-10-28T01:00:00Z"));

        assertThrows(IllegalArgumentException.class, () -> repository.replaceIfCurrentStatus(
                "tenant-b", order.id(), MarketplaceOrderStatus.CREATED, submitted));

        MarketplaceOrderLine changed = new MarketplaceOrderLine("line-1", "different-product",
                "offer-1", "seller-1", "Fresh produce", new BigDecimal("2"), "kg",
                new BigDecimal("12500"), "IDR");
        MarketplaceOrder altered = MarketplaceOrder.reconstitute(order.id(), "tenant-a", order.buyerId(),
                List.of(changed), "IDR", order.createdAt(), submitted.updatedAt(),
                MarketplaceOrderStatus.SUBMITTED, null);
        assertThrows(IllegalArgumentException.class, () -> repository.replaceIfCurrentStatus(
                "tenant-a", order.id(), MarketplaceOrderStatus.CREATED, altered));
    }

    private static MarketplaceOrder order() {
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        MarketplaceOrderLine line = new MarketplaceOrderLine("line-1", "produce-1", "offer-1", "seller-1",
                "Fresh produce", new BigDecimal("2.000"), "kg", new BigDecimal("12500.00"), "IDR");
        return MarketplaceOrder.create(UUID.randomUUID(), "tenant-a", "buyer-a", List.of(line), "IDR", now);
    }
}