package tech.kayys.numou.automotiveparts.purchasing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.inventory.adapter.postgres.PostgresInventoryRepository;
import tech.kayys.numou.inventory.application.InventoryService;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresGoodsReceiptInventoryPostingTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DataSource dataSource;
    private ItemId itemId;
    private WarehouseId warehouseId;

    @BeforeEach void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl()); ds.setUser(POSTGRES.getUsername()); ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        itemId = ItemId.of("part-filter-" + suffix); warehouseId = WarehouseId.of("warehouse-bandung-" + suffix);
        InventoryService inventory = new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a"));
        inventory.registerItem(new InventoryItem(itemId, "FILTER-" + suffix, "Oil filter",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "EA"));
        inventory.registerWarehouse(new Warehouse(warehouseId, "BDG-" + suffix, "Bandung parts store", "Bandung"));
    }

    @Test void receiptReplayAfterRepositoryRecreationPostsExactlyOneMovement() {
        GoodsReceipt receipt = receipt("receipt-durable-1", "2");
        var mapping = mapping();
        var first = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping).post(receipt);
        var replay = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping).post(receipt);
        var movements = new PostgresInventoryRepository(dataSource, "tenant-a").findByItemAndWarehouse(itemId, warehouseId);
        assertEquals(first.id(), replay.id()); assertEquals(1, movements.size());
        assertEquals(new BigDecimal("24.00000000"), movements.get(0).quantity());
    }

    @Test void sameReceiptKeyWithChangedQuantityIsRejectedWithoutAdditionalMovement() {
        var service = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping());
        service.post(receipt("receipt-durable-2", "2"));
        assertThrows(IllegalArgumentException.class, () -> service.post(receipt("receipt-durable-2", "3")));
        assertEquals(1, new PostgresInventoryRepository(dataSource, "tenant-a")
                .findByItemAndWarehouse(itemId, warehouseId).size());
    }

    @Test void beginReplayDoesNotIncrementFailureAttemptCounter() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        var first = ledger.begin("tenant-a", "receipt-ledger-1", "fingerprint-a", 3);
        var replay = ledger.begin("tenant-a", "receipt-ledger-1", "fingerprint-a", 3);
        assertEquals(0, first.attempts()); assertEquals(0, replay.attempts());
        ledger.markPosted("tenant-a", "receipt-ledger-1", "fingerprint-a", "movement-1");
        var recovered = ledger.begin("tenant-a", "receipt-ledger-1", "fingerprint-a", 3);
        assertEquals(ReceiptPostingLedger.Status.POSTED, recovered.status());
        assertEquals("movement-1", recovered.movementId()); assertEquals(0, recovered.attempts());
        assertThrows(IllegalArgumentException.class,
                () -> ledger.begin("tenant-a", "receipt-ledger-1", "different-fingerprint", 3));
    }

    @Test void retryExhaustionAtomicallyDeadLettersAndOperatorRequeueIsAudited() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        ledger.begin("tenant-a", "receipt-dead-1", "fingerprint-dead", 2);
        ledger.markRetryable("tenant-a", "receipt-dead-1", "fingerprint-dead", "temporary failure", 2);
        var pending = ledger.begin("tenant-a", "receipt-dead-1", "fingerprint-dead", 2);
        assertEquals(1, pending.attempts()); assertEquals(ReceiptPostingLedger.Status.PENDING, pending.status());
        ledger.markRetryable("tenant-a", "receipt-dead-1", "fingerprint-dead", "final failure", 2);
        var dead = ledger.begin("tenant-a", "receipt-dead-1", "fingerprint-dead", 2);
        assertEquals(ReceiptPostingLedger.Status.DEAD_LETTER, dead.status());
        assertEquals(2, dead.attempts()); assertNotNull(dead.deadLetteredAt());
        assertEquals("final failure", dead.lastFailure());

        var requeue = new RequeueReceiptPosting(ledger, (tenant, actor, receipt) ->
                tenant.equals("tenant-a") && actor.equals("operator-7"));
        requeue.execute("tenant-a", "receipt-dead-1", "fingerprint-dead", "operator-7", "mapping corrected");
        var restored = ledger.begin("tenant-a", "receipt-dead-1", "fingerprint-dead", 2);
        assertEquals(ReceiptPostingLedger.Status.PENDING, restored.status());
        assertEquals(0, restored.attempts()); assertNotNull(restored.requeuedAt());
        assertEquals("operator-7", restored.requeuedBy()); assertEquals("mapping corrected", restored.requeueReason());
        assertThrows(IllegalStateException.class, () ->
                requeue.execute("tenant-a", "receipt-dead-1", "fingerprint-dead", "operator-7", "duplicate requeue"));
    }

    @Test void unauthorizedAndCrossTenantRequeueAreRejected() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        ledger.begin("tenant-a", "receipt-dead-2", "fingerprint-dead-2", 1);
        ledger.markRetryable("tenant-a", "receipt-dead-2", "fingerprint-dead-2", "failure", 1);
        var denied = new RequeueReceiptPosting(ledger, (tenant, actor, receipt) -> false);
        assertThrows(SecurityException.class, () -> denied.execute(
                "tenant-a", "receipt-dead-2", "fingerprint-dead-2", "operator-x", "retry"));
        var allowed = new RequeueReceiptPosting(ledger, (tenant, actor, receipt) -> true);
        assertThrows(IllegalStateException.class, () -> allowed.execute(
                "tenant-b", "receipt-dead-2", "fingerprint-dead-2", "operator-x", "cross tenant"));
        assertEquals(ReceiptPostingLedger.Status.DEAD_LETTER,
                ledger.begin("tenant-a", "receipt-dead-2", "fingerprint-dead-2").status());
    }

    @Test void durablePostingLedgerKeepsTenantsIsolated() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        ledger.begin("tenant-a", "same-receipt", "fingerprint-a");
        var other = ledger.begin("tenant-b", "same-receipt", "fingerprint-b");
        assertEquals("tenant-b", other.tenantId()); assertEquals(ReceiptPostingLedger.Status.PENDING, other.status());
    }

    @Test void pendingLedgerQueryReturnsOnlyStalePendingRows() throws Exception {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String id = "receipt-stale-query-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", id, "fingerprint-stale");
        ageRow(id);
        var stale = ledger.findPending(Instant.now().minusSeconds(60), 1000);
        assertTrue(stale.stream().anyMatch(r -> r.receiptId().equals(id)));
        assertTrue(stale.stream().allMatch(r -> r.status() == ReceiptPostingLedger.Status.PENDING));
    }

    @Test void pendingPostingClaimIsExclusiveUntilLeaseExpires() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String id = "receipt-claim-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", id, "fingerprint-claim"); ageRowUnchecked(id);
        Instant cutoff = Instant.now().minusSeconds(60);
        var first = ledger.claimPending(cutoff, "worker-a", Instant.now().plusSeconds(120), 100);
        assertTrue(first.stream().anyMatch(r -> r.receiptId().equals(id)));
        var second = ledger.claimPending(cutoff, "worker-b", Instant.now().plusSeconds(120), 100);
        assertFalse(second.stream().anyMatch(r -> r.receiptId().equals(id)));
    }

    @Test void expiredLeaseCanBeReclaimedWithoutAnotherStaleAgeWindow() throws Exception {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String id = "receipt-lease-expiry-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", id, "fingerprint-expiry"); ageRow(id);
        Instant cutoff = Instant.now().minusSeconds(60);
        var first = ledger.claimPending(cutoff, "worker-a", Instant.now().plusSeconds(120), 100);
        assertTrue(first.stream().anyMatch(r -> r.receiptId().equals(id)));
        try (var c = dataSource.getConnection(); var ps = c.prepareStatement("""
                UPDATE purchasing_receipt_inventory_posting SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second'
                WHERE tenant_id='tenant-a' AND receipt_id=?
                """)) { ps.setString(1, id); assertEquals(1, ps.executeUpdate()); }
        var reclaimed = ledger.claimPending(cutoff, "worker-b", Instant.now().plusSeconds(120), 100);
        assertTrue(reclaimed.stream().anyMatch(r -> r.receiptId().equals(id)));
    }

    private GoodsReceipt receipt(String id, String quantity) {
        return new GoodsReceipt(id, "tenant-a", "ledger-a", "PO-100", "PO-100-L1", new BigDecimal(quantity), "receiver-1");
    }
    private GoodsReceiptInventoryPostingService.ReceiptInventoryMapping mapping() {
        return (tenant, po, line) -> new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(
                itemId, warehouseId, new BigDecimal("12"), new BigDecimal("25000"), "IDR");
    }
    private void ageRow(String id) throws Exception {
        try (var c = dataSource.getConnection(); var ps = c.prepareStatement("""
                UPDATE purchasing_receipt_inventory_posting SET updated_at=CURRENT_TIMESTAMP-INTERVAL '10 minutes'
                WHERE tenant_id='tenant-a' AND receipt_id=?
                """)) { ps.setString(1, id); assertEquals(1, ps.executeUpdate()); }
    }
    private void ageRowUnchecked(String id) {
        try { ageRow(id); } catch (Exception e) { throw new AssertionError(e); }
    }
}
