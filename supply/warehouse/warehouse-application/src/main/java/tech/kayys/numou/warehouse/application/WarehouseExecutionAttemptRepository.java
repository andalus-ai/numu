package tech.kayys.numou.warehouse.application;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for cross-context execution recovery.
 *
 * <p>Implementations must enforce tenant-scoped uniqueness on (tenant_id, command_key),
 * atomically compare-and-set by expected version, and preserve the attempt across process
 * restarts. A process-local map is not a production implementation.</p>
 */
public interface WarehouseExecutionAttemptRepository {
    /** Insert once; a duplicate key with a different fingerprint must fail. */
    WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt);

    Optional<WarehouseExecutionAttempt> find(String tenantId, String commandKey);

    /** Persist candidate only when the stored version equals expectedVersion. */
    WarehouseExecutionAttempt transition(String tenantId, String commandKey,
                                         long expectedVersion, WarehouseExecutionAttempt candidate);

    /** Return oldest recoverable attempts for bounded background polling. */
    List<WarehouseExecutionAttempt> findRecoverable(int limit);
}
