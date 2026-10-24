package tech.kayys.numou.warehouse.application;

import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;

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

    /**
     * Persist a retry eligibility timestamp using compare-and-set version fencing.
     * The attempt remains in reconciliation state and is not claimable before the timestamp.
     */
    default WarehouseExecutionAttempt scheduleRetry(String tenantId, String commandKey,
                                                     long expectedVersion, Instant nextEligibleAt) {
        throw new UnsupportedOperationException("Durable retry scheduling is not supported by this repository");
    }

    /**
     * Atomically schedule a retry and relinquish the exact live claim in one database operation.
     * This avoids a crash window between releasing a lease and persisting retry eligibility.
     */
    default WarehouseExecutionAttempt scheduleRetry(WarehouseExecutionClaim claim, Instant nextEligibleAt) {
        throw new UnsupportedOperationException("Atomic claim retry scheduling is not supported by this repository");
    }

    /** Atomically terminalize a claimed reconciliation attempt and clear its lease. */
    default WarehouseExecutionAttempt deadLetter(WarehouseExecutionClaim claim, String reason) {
        throw new UnsupportedOperationException("Atomic claimed dead-letter transition is not supported by this repository");
    }

    /** Return a bounded, oldest-first view of terminal recovery records for operations tooling. */
    default List<WarehouseExecutionAttempt> findDeadLetters(int limit) {
        throw new UnsupportedOperationException("Dead-letter queries are not supported by this repository");
    }

    /** Tenant-scoped dead-letter inspection for authenticated operator workflows. */
    default List<WarehouseExecutionAttempt> findDeadLetters(String tenantId, int limit) {
        throw new UnsupportedOperationException("Tenant-scoped dead-letter queries are not supported by this repository");
    }

    /** Tenant-scoped point-in-time operational counters. */
    default WarehouseRecoveryHealthSummary summarizeRecovery(String tenantId) {
        throw new UnsupportedOperationException("Recovery health summaries are not supported by this repository");
    }

    /** Version-fenced manual replay; implementation must atomically reset status and eligibility. */
    default WarehouseExecutionAttempt replayDeadLetter(String tenantId, String commandKey,
                                                       long expectedVersion, String operatorReason,
                                                       Instant nextEligibleAt) {
        return replayDeadLetter(tenantId, commandKey, expectedVersion, "legacy-unknown",
                operatorReason, null, nextEligibleAt);
    }

    /** Version-fenced replay with explicit actor and correlation metadata for durable audit. */
    default WarehouseExecutionAttempt replayDeadLetter(String tenantId, String commandKey,
                                                       long expectedVersion, String actorId,
                                                       String operatorReason, String correlationId,
                                                       Instant nextEligibleAt) {
        throw new UnsupportedOperationException("Audited manual dead-letter replay is not supported by this repository");
    }

    /** Return immutable operator actions for one tenant, newest first, with a bounded result size. */
    default List<WarehouseRecoveryOperatorAuditEvent> findOperatorAuditEvents(String tenantId, int limit) {
        throw new UnsupportedOperationException("Recovery operator audit queries are not supported by this repository");
    }

    /**
     * Atomically claim recoverable attempts for one worker. Implementations must use
     * durable leases and ensure competing workers do not receive the same live claim.
     */
    default List<WarehouseExecutionClaim> claimRecoverable(String workerId, int limit, Duration lease) {
        throw new UnsupportedOperationException("Durable recovery claims are not supported by this repository");
    }

    /**
     * Renew a claim only if the exact lease is still current and unexpired.
     * The returned claim carries the new lease deadline and must replace the old claim.
     */
    default WarehouseExecutionClaim renewClaim(WarehouseExecutionClaim claim, Duration lease) {
        throw new UnsupportedOperationException("Fenced durable lease renewal is not supported by this repository");
    }

    /**
     * Release only the exact lease returned by claimRecoverable/renewClaim.
     * Passing the claim (including its lease deadline) fences out stale workers.
     */
    default void releaseClaim(WarehouseExecutionClaim claim) {
        throw new UnsupportedOperationException("Fenced durable claim release is not supported by this repository");
    }

    /**
     * @deprecated Worker IDs are not fencing tokens. Use releaseClaim(WarehouseExecutionClaim)
     * so an old execution cannot release a later lease acquired by the same worker identity.
     */
    @Deprecated
    default void releaseClaim(String tenantId, String commandKey, String workerId) {
        throw new UnsupportedOperationException("Use releaseClaim(WarehouseExecutionClaim) for lease fencing");
    }
}
