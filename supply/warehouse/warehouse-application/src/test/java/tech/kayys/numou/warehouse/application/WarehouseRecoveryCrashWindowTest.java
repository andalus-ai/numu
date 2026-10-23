package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.warehouse.domain.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure-injection tests for the two most important inventory/warehouse crash windows.
 * These tests exercise application semantics with deterministic in-memory adapters; they do
 * not replace the PostgreSQL integration test or a process-level restart test.
 */
class WarehouseRecoveryCrashWindowTest {
    @Test
    void inventoryPostedButAcknowledgementLostReplaysWithoutDuplicateMovement() {
        Fixture f = new Fixture(false);
        f.inventory.failAfterFirstPost = true;

        assertThrows(IllegalStateException.class, () -> f.receive());
        assertEquals(1, f.inventory.physicalPosts);
        assertEquals(WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION,
                f.attempts.stored("tenant-a", "receive-1").status());

        f.recover();

        assertEquals(1, f.inventory.physicalPosts, "same idempotency key must replay original inventory movement");
        assertEquals(new BigDecimal("1"), f.work.find("tenant-a", f.workOrderId).orElseThrow()
                .lines().get(0).executedQuantity());
        assertEquals(WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED,
                f.attempts.stored("tenant-a", "receive-1").status());
    }

    @Test
    void warehouseCommitBeforeJournalAcknowledgementReplaysWarehouseReceipt() {
        Fixture f = new Fixture(true);

        assertThrows(IllegalStateException.class, f::receive);
        assertEquals(1, f.inventory.physicalPosts);
        assertEquals(1, f.work.newCommits);
        assertEquals(WarehouseExecutionAttemptStatus.NEEDS_RECONCILIATION,
                f.attempts.stored("tenant-a", "receive-1").status());

        f.recover();

        assertEquals(1, f.inventory.physicalPosts, "inventory movement must not be duplicated");
        assertEquals(1, f.work.newCommits, "warehouse command receipt must replay the committed aggregate");
        assertTrue(f.work.receiptReplays > 0);
        assertEquals(new BigDecimal("1"), f.work.find("tenant-a", f.workOrderId).orElseThrow()
                .lines().get(0).executedQuantity());
        assertEquals(WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED,
                f.attempts.stored("tenant-a", "receive-1").status());
    }

    private static final class Fixture {
        final FakeWorkRepository work = new FakeWorkRepository();
        final FakeAttemptRepository attempts;
        final FakeInventory inventory = new FakeInventory();
        final WarehouseExecutionService warehouse = new WarehouseExecutionService(work);
        final WarehouseExecutionRecoveryService recovery;
        final RecoverableWarehouseInventoryExecutionCoordinator coordinator;
        final String workOrderId;

        Fixture(boolean failJournalCommit) {
            attempts = new FakeAttemptRepository(failJournalCommit);
            recovery = new WarehouseExecutionRecoveryService(attempts);
            WarehouseWorkOrder order = warehouse.create("tenant-a", "create-1", "inbound-1", null,
                    WarehouseWorkType.RECEIVING,
                    List.of(WarehouseWorkLine.planned("line-1", "item-1", "each", BigDecimal.ONE)));
            workOrderId = order.id();
            warehouse.start("tenant-a", workOrderId, "start-1");
            coordinator = new RecoverableWarehouseInventoryExecutionCoordinator(work, warehouse, inventory, recovery);
        }

        WarehouseWorkOrder receive() {
            return coordinator.receive("tenant-a", workOrderId, "line-1", "item-1", "wh-1",
                    BigDecimal.ONE, new BigDecimal("2.50"), "USD", "receive-1", "receipt-ref-1");
        }

        void recover() {
            WarehouseExecutionAttempt attempt = attempts.stored("tenant-a", "receive-1");
            new WarehouseInventoryRecoveryHandler(coordinator).recover(attempt);
        }
    }

    private static final class FakeInventory implements InventoryWarehouseGateway {
        final Set<String> posted = new HashSet<>();
        int physicalPosts;
        boolean failAfterFirstPost;

        @Override public void receive(String tenant, String item, String warehouse, BigDecimal quantity,
                BigDecimal unitCost, String currency, String reference, String commandKey, String workOrder, String line) {
            if (posted.add(tenant + "|" + commandKey)) {
                physicalPosts++;
                if (failAfterFirstPost) {
                    failAfterFirstPost = false;
                    throw new IllegalStateException("simulated lost acknowledgement after inventory post");
                }
            }
        }

        @Override public void issueReserved(String tenant, String reservation, String item, String warehouse,
                BigDecimal quantity, String currency, String reference, String commandKey, String workOrder, String line) {
            throw new UnsupportedOperationException("not used in receiving crash-window tests");
        }
    }

    private static final class FakeWorkRepository implements WarehouseWorkRepository {
        final Map<String, WarehouseWorkOrder> orders = new HashMap<>();
        final Map<String, WarehouseCommandReceipt> receipts = new HashMap<>();
        int newCommits;
        int receiptReplays;

        private String key(String tenant, String value) { return tenant + "|" + value; }
        @Override public Optional<WarehouseWorkOrder> find(String tenant, String id) {
            return Optional.ofNullable(orders.get(key(tenant, id)));
        }
        @Override public Optional<WarehouseCommandReceipt> findReceipt(String tenant, String command) {
            return Optional.ofNullable(receipts.get(key(tenant, command)));
        }
        @Override public WarehouseCommitResult commit(String tenant, long expectedVersion, WarehouseWorkOrder candidate,
                String command, String fingerprint, List<WarehouseOutboxEvent> events) {
            String k = key(tenant, command);
            WarehouseCommandReceipt existing = receipts.get(k);
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint))
                    throw new IllegalStateException("command key reused with different payload");
                receiptReplays++;
                return new WarehouseCommitResult(existing.result(), true);
            }
            WarehouseWorkOrder current = orders.get(key(tenant, candidate.id()));
            if (expectedVersion == 0) {
                if (current != null) throw new IllegalStateException("duplicate work order");
            } else if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("stale work order version");
            }
            orders.put(key(tenant, candidate.id()), candidate);
            receipts.put(k, new WarehouseCommandReceipt(tenant, command, fingerprint, candidate, Instant.now()));
            newCommits++;
            return new WarehouseCommitResult(candidate, false);
        }
    }

    private static final class FakeAttemptRepository implements WarehouseExecutionAttemptRepository {
        final Map<String, WarehouseExecutionAttempt> attempts = new HashMap<>();
        boolean failNextWarehouseCommitTransition;
        FakeAttemptRepository(boolean failJournalCommit) { failNextWarehouseCommitTransition = failJournalCommit; }
        private String key(String tenant, String command) { return tenant + "|" + command; }
        WarehouseExecutionAttempt stored(String tenant, String command) { return attempts.get(key(tenant, command)); }

        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) {
            return attempts.computeIfAbsent(key(attempt.tenantId(), attempt.commandKey()), ignored -> attempt);
        }
        @Override public Optional<WarehouseExecutionAttempt> find(String tenant, String command) {
            return Optional.ofNullable(attempts.get(key(tenant, command)));
        }
        @Override public WarehouseExecutionAttempt transition(String tenant, String command, long version,
                WarehouseExecutionAttempt candidate) {
            String k = key(tenant, command);
            WarehouseExecutionAttempt current = attempts.get(k);
            if (current == null || current.version() != version) throw new IllegalStateException("stale journal version");
            if (failNextWarehouseCommitTransition
                    && candidate.status() == WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED) {
                failNextWarehouseCommitTransition = false;
                throw new IllegalStateException("simulated crash before journal commit acknowledgement");
            }
            attempts.put(k, candidate);
            return candidate;
        }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) {
            return attempts.values().stream()
                    .filter(a -> a.status() != WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED)
                    .limit(limit).toList();
        }
        @Override public List<WarehouseExecutionClaim> claimRecoverable(String worker, int limit, Duration lease) {
            return findRecoverable(limit).stream()
                    .map(a -> new WarehouseExecutionClaim(a, worker, Instant.now().plus(lease))).toList();
        }
        @Override public void releaseClaim(String tenant, String command, String worker) { }
    }
}
