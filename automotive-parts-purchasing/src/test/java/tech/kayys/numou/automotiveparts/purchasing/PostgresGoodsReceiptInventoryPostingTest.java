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
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private ItemId itemId;
    private WarehouseId warehouseId;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        // Each test owns a distinct stock aggregate so movement-count assertions are isolated
        // even though the Testcontainers database is shared for the test class lifetime.
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        itemId = ItemId.of("part-filter-" + suffix);
        warehouseId = WarehouseId.of("warehouse-bandung-" + suffix);
        InventoryService inventory = new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a"));
        inventory.registerItem(new InventoryItem(itemId, "FILTER-" + suffix, "Oil filter",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "EA"));
        inventory.registerWarehouse(new Warehouse(warehouseId, "BDG-" + suffix, "Bandung parts store", "Bandung"));
    }

    @Test
    void receiptReplayAfterRepositoryRecreationPostsExactlyOneMovement() {
        GoodsReceipt receipt = new GoodsReceipt("receipt-durable-1", "tenant-a", "ledger-a",
                "PO-100", "PO-100-L1", new BigDecimal("2"), "receiver-1");
        var mapping = (GoodsReceiptInventoryPostingService.ReceiptInventoryMapping) (tenant, po, line) ->
                new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(itemId, warehouseId,
                        new BigDecimal("12"), new BigDecimal("25000"), "IDR");

        var first = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping).post(receipt);

        // A new repository/service instance simulates process restart; the database is the idempotency authority.
        var replay = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping).post(receipt);

        var repository = new PostgresInventoryRepository(dataSource, "tenant-a");
        assertEquals(first.id(), replay.id());
        assertEquals(1, repository.findByItemAndWarehouse(itemId, warehouseId).size());
        assertEquals(new BigDecimal("24.00000000"),
                repository.findByItemAndWarehouse(itemId, warehouseId).get(0).quantity());
    }

    @Test
    void sameReceiptKeyWithChangedQuantityIsRejectedWithoutAdditionalMovement() {
        var mapping = (GoodsReceiptInventoryPostingService.ReceiptInventoryMapping) (tenant, po, line) ->
                new GoodsReceiptInventoryPostingService.ReceiptInventoryTarget(itemId, warehouseId,
                        BigDecimal.ONE, new BigDecimal("25000"), "IDR");
        var service = new GoodsReceiptInventoryPostingService(
                new InventoryService(new PostgresInventoryRepository(dataSource, "tenant-a")), mapping);

        service.post(receipt("receipt-durable-2", "2"));
        assertThrows(IllegalArgumentException.class, () -> service.post(receipt("receipt-durable-2", "3")));
        assertEquals(1, new PostgresInventoryRepository(dataSource, "tenant-a")
                .findByItemAndWarehouse(itemId, warehouseId).size());
    }

    @Test
    void durablePostingLedgerRecoversPostedStateAndRejectsChangedPayload() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        var first = ledger.begin("tenant-a", "receipt-ledger-1", "fingerprint-a");
        assertEquals(ReceiptPostingLedger.Status.PENDING, first.status());
        ledger.markPosted("tenant-a", "receipt-ledger-1", "fingerprint-a", "movement-1");

        // A new adapter instance reads the committed lifecycle from PostgreSQL.
        var recovered = new JdbcReceiptPostingLedger(dataSource)
                .begin("tenant-a", "receipt-ledger-1", "fingerprint-a");
        assertEquals(ReceiptPostingLedger.Status.POSTED, recovered.status());
        assertEquals("movement-1", recovered.movementId());
        assertEquals(2, recovered.attempts());
        assertThrows(IllegalArgumentException.class,
                () -> ledger.begin("tenant-a", "receipt-ledger-1", "different-fingerprint"));
    }

    @Test
    void durablePostingLedgerKeepsTenantsIsolated() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        ledger.begin("tenant-a", "same-receipt", "fingerprint-a");
        var otherTenant = ledger.begin("tenant-b", "same-receipt", "fingerprint-b");
        assertEquals("tenant-b", otherTenant.tenantId());
        assertEquals(ReceiptPostingLedger.Status.PENDING, otherTenant.status());
    }

    @Test
    void pendingLedgerQueryReturnsOnlyStalePendingRows() throws Exception {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String receiptId = "receipt-stale-query-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", receiptId, "fingerprint-stale");
        try (var connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE purchasing_receipt_inventory_posting
                     SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                     WHERE tenant_id = ? AND receipt_id = ?
                     """)) {
            update.setString(1, "tenant-a");
            update.setString(2, receiptId);
            assertEquals(1, update.executeUpdate());
        }
        var stale = ledger.findPending(Instant.now().minusSeconds(60), 1000);
        assertTrue(stale.stream().anyMatch(record -> record.receiptId().equals(receiptId)));
        assertTrue(stale.stream().allMatch(record -> record.status() == ReceiptPostingLedger.Status.PENDING));
    }

    @Test
    void pendingPostingClaimIsExclusiveUntilLeaseExpires() {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String receiptId = "receipt-claim-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", receiptId, "fingerprint-claim");
        try (var connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE purchasing_receipt_inventory_posting
                     SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                     WHERE tenant_id = ? AND receipt_id = ?
                     """)) {
            update.setString(1, "tenant-a");
            update.setString(2, receiptId);
            assertEquals(1, update.executeUpdate());
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        Instant staleBefore = Instant.now().minusSeconds(60);
        var firstClaim = ledger.claimPending(staleBefore, "worker-a", Instant.now().plusSeconds(120), 100);
        assertTrue(firstClaim.stream().anyMatch(record -> record.receiptId().equals(receiptId)));
        var competingClaim = ledger.claimPending(staleBefore, "worker-b", Instant.now().plusSeconds(120), 100);
        assertFalse(competingClaim.stream().anyMatch(record -> record.receiptId().equals(receiptId)));
    }

    @Test
    void expiredLeaseCanBeReclaimedWithoutAnotherStaleAgeWindow() throws Exception {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        String receiptId = "receipt-lease-expiry-" + java.util.UUID.randomUUID();
        ledger.begin("tenant-a", receiptId, "fingerprint-expiry");
        try (var connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE purchasing_receipt_inventory_posting
                     SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                     WHERE tenant_id = ? AND receipt_id = ?
                     """)) {
            update.setString(1, "tenant-a");
            update.setString(2, receiptId);
            assertEquals(1, update.executeUpdate());
        }

        Instant staleBefore = Instant.now().minusSeconds(60);
        var firstClaim = ledger.claimPending(staleBefore, "worker-a", Instant.now().plusSeconds(120), 100);
        assertTrue(firstClaim.stream().anyMatch(record -> record.receiptId().equals(receiptId)));

        try (var connection = dataSource.getConnection();
             PreparedStatement expire = connection.prepareStatement("""
                     UPDATE purchasing_receipt_inventory_posting
                     SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                     WHERE tenant_id = ? AND receipt_id = ?
                     """)) {
            expire.setString(1, "tenant-a");
            expire.setString(2, receiptId);
            assertEquals(1, expire.executeUpdate());
        }

        var reclaimed = ledger.claimPending(staleBefore, "worker-b", Instant.now().plusSeconds(120), 100);
        assertTrue(reclaimed.stream().anyMatch(record -> record.receiptId().equals(receiptId)),
                "expired lease must be reclaimable without waiting for updatedBefore to advance");
    }

    private GoodsReceipt receipt(String id, String quantity) {
        return new GoodsReceipt(id, "tenant-a", "ledger-a", "PO-100", "PO-100-L1",
                new BigDecimal(quantity), "receiver-1");
    }
}
