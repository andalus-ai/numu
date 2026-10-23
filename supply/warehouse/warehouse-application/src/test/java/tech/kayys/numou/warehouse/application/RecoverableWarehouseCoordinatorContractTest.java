package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RecoverableWarehouseCoordinatorContractTest {
    @Test void journalRejectsReusingCommandKeyForDifferentPayload() {
        InMemoryAttempts repository = new InMemoryAttempts();
        WarehouseExecutionRecoveryService service = new WarehouseExecutionRecoveryService(repository);
        service.prepare("tenant-a", "cmd-1", "fingerprint-a", "work-1", "line-1", "receive");
        assertThrows(IllegalStateException.class,
                () -> service.prepare("tenant-a", "cmd-1", "fingerprint-b", "work-1", "line-1", "receive"));
    }

    @Test void recoveryStateCanResumeAfterInventoryPosting() {
        InMemoryAttempts repository = new InMemoryAttempts();
        WarehouseExecutionRecoveryService service = new WarehouseExecutionRecoveryService(repository);
        service.prepare("tenant-a", "cmd-2", "fp", "work-1", "line-1", "pick");
        var posted = service.inventoryPosted("tenant-a", "cmd-2");
        assertEquals(WarehouseExecutionAttemptStatus.INVENTORY_POSTED, posted.status());
        assertEquals(WarehouseExecutionAttemptStatus.INVENTORY_POSTED,
                service.recoverable(10).get(0).status());
    }

    private static final class InMemoryAttempts implements WarehouseExecutionAttemptRepository {
        private final java.util.Map<String, WarehouseExecutionAttempt> data = new java.util.HashMap<>();
        private String key(String tenant, String command) { return tenant + "|" + command; }
        @Override public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt a) {
            return data.computeIfAbsent(key(a.tenantId(), a.commandKey()), ignored -> a);
        }
        @Override public Optional<WarehouseExecutionAttempt> find(String tenant, String command) {
            return Optional.ofNullable(data.get(key(tenant, command)));
        }
        @Override public WarehouseExecutionAttempt transition(String tenant, String command, long version,
                WarehouseExecutionAttempt candidate) {
            String key = key(tenant, command);
            WarehouseExecutionAttempt old = data.get(key);
            if (old == null || old.version() != version) throw new IllegalStateException("stale attempt");
            data.put(key, candidate);
            return candidate;
        }
        @Override public List<WarehouseExecutionAttempt> findRecoverable(int limit) {
            return data.values().stream().filter(a -> a.status() != WarehouseExecutionAttemptStatus.WAREHOUSE_COMMITTED)
                    .limit(limit).toList();
        }
    }
}
