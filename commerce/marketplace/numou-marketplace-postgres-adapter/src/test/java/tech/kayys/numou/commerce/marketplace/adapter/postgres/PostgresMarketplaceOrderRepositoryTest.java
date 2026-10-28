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
import tech.kayys.numou.commerce.marketplace.application.checkout.MarketplaceCheckout;
import tech.kayys.numou.commerce.marketplace.application.checkout.MarketplaceCheckoutStatus;

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
                statement.execute("DROP TABLE IF EXISTS marketplace_checkout_reservation CASCADE");
                statement.execute("DROP TABLE IF EXISTS marketplace_checkout CASCADE");
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
                try (InputStream thirdMigration = getClass().getResourceAsStream(
                        "/db/migration/V3__marketplace_checkout_saga.sql")) {
                    assertNotNull(thirdMigration, "checkout saga migration must be packaged");
                    String thirdSql = new String(thirdMigration.readAllBytes(), StandardCharsets.UTF_8);
                    for (String command : thirdSql.split(";")) {
                        if (!command.isBlank()) statement.execute(command);
                    }
                }
                try (InputStream fourthMigration = getClass().getResourceAsStream(
                        "/db/migration/V4__marketplace_checkout_request_snapshot.sql")) {
                    assertNotNull(fourthMigration, "checkout request snapshot migration must be packaged");
                    String fourthSql = new String(fourthMigration.readAllBytes(), StandardCharsets.UTF_8);
                    for (String command : fourthSql.split(";")) {
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
        assertEquals(1, loaded.lines().size());
        MarketplaceOrderLine originalLine = order.lines().get(0);
        MarketplaceOrderLine loadedLine = loaded.lines().get(0);
        assertEquals(originalLine.lineId(), loadedLine.lineId());
        assertEquals(originalLine.productId(), loadedLine.productId());
        assertEquals(originalLine.offeringId(), loadedLine.offeringId());
        assertEquals(originalLine.sellerId(), loadedLine.sellerId());
        assertEquals(originalLine.description(), loadedLine.description());
        assertEquals(0, originalLine.quantity().compareTo(loadedLine.quantity()));
        assertEquals(originalLine.unit(), loadedLine.unit());
        assertEquals(0, originalLine.unitPrice().compareTo(loadedLine.unitPrice()));
        assertEquals(originalLine.currency(), loadedLine.currency());
        assertEquals(MarketplaceOrderStatus.CREATED, loaded.status());
        assertTrue(repository.findByTenantAndId("tenant-b", order.id()).isEmpty());
    }

    @Test
    void idempotentCreationReturnsTheOriginalOrderAndRejectsChangedRequest() {
        MarketplaceOrder proposed = order();
        String fingerprint = MarketplaceOrderRequestFingerprint.calculate(
                proposed.tenantId(), proposed.buyerId(), proposed.currency(), proposed.lines());

        MarketplaceOrder retryCandidate = order();
        MarketplaceOrder first = repository.createOrGet("tenant-a", "checkout-1", fingerprint, proposed);
        MarketplaceOrder retry = repository.createOrGet("tenant-a", "checkout-1", fingerprint, retryCandidate);

        assertEquals(first.id(), retry.id());
        assertTrue(repository.findByTenantAndId("tenant-a", retryCandidate.id()).isEmpty(),
                "a replay must not persist the newly proposed duplicate order");
        MarketplaceOrderLine changedLine = new MarketplaceOrderLine("line-1", "different-product",
                "offer-1", "seller-1", "Fresh produce", new BigDecimal("2.000"), "kg",
                new BigDecimal("12500.00"), "IDR");
        MarketplaceOrder changedRequest = MarketplaceOrder.create(UUID.randomUUID(), "tenant-a", "buyer-a",
                List.of(changedLine), "IDR", Instant.parse("2026-10-28T00:00:00Z"));
        String changedFingerprint = MarketplaceOrderRequestFingerprint.calculate(
                changedRequest.tenantId(), changedRequest.buyerId(), changedRequest.currency(), changedRequest.lines());
        assertThrows(MarketplaceOrderIdempotencyConflictException.class, () ->
                repository.createOrGet("tenant-a", "checkout-1", changedFingerprint, changedRequest));
    }

    @Test
    void persistsCheckoutCheckpointsAndUsesVersionedCompareAndSet() {
        MarketplaceOrder order = order();
        repository.create(order);
        var checkoutRepository = new PostgresMarketplaceCheckoutRepository(dataSource());
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        MarketplaceCheckout proposed = MarketplaceCheckout.start(UUID.randomUUID(), "tenant-a", order.id(),
                "checkout-saga-1", "a".repeat(64), "warehouse-1", now.plusSeconds(900), now);

        MarketplaceCheckout created = checkoutRepository.createOrGet(proposed);
        assertEquals(1, checkoutRepository.findRecoverable("tenant-a", 10).size(),
                "a process interrupted before its first reservation must remain recoverable");
        assertEquals(MarketplaceCheckoutStatus.RESERVING_INVENTORY,
                checkoutRepository.findRecoverable("tenant-a", 10).get(0).status());
        MarketplaceCheckout withReservation = created.recordReservation("line-1", "reservation-1", now.plusSeconds(1));
        assertTrue(checkoutRepository.saveIfVersion("tenant-a", created.id(), 0, withReservation));
        MarketplaceCheckout loaded = checkoutRepository.findByTenantAndId("tenant-a", created.id()).orElseThrow();

        assertEquals(1, loaded.version());
        assertEquals(MarketplaceCheckoutStatus.RESERVING_INVENTORY, loaded.status());
        assertEquals("reservation-1", loaded.reservationIds().get("line-1"));
        assertTrue(checkoutRepository.findByTenantAndId("tenant-b", created.id()).isEmpty());
        assertFalse(checkoutRepository.saveIfVersion("tenant-a", created.id(), 0, withReservation));

        MarketplaceCheckout awaiting = loaded.awaitingPayment("payment-1", now.plusSeconds(2));
        assertTrue(checkoutRepository.saveIfVersion("tenant-a", created.id(), 1, awaiting));
        assertEquals(1, checkoutRepository.findRecoverable("tenant-a", 10).size());
        assertEquals("tenant-a", checkoutRepository.findRecoverable("tenant-a", 10).get(0).tenantId());
        assertTrue(checkoutRepository.findRecoverable("tenant-b", 10).isEmpty());

        assertThrows(IllegalStateException.class, () -> checkoutRepository.createOrGet(
                MarketplaceCheckout.start(UUID.randomUUID(), "tenant-a", order.id(), "checkout-saga-1",
                        "b".repeat(64), "warehouse-1", now.plusSeconds(900), now)));
    }

    private static org.postgresql.ds.PGSimpleDataSource dataSource() {
        var source = new org.postgresql.ds.PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
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