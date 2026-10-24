package tech.kayys.numou.warehouse.application;

/**
 * Signals a storage-layer failure that may be transient and must not automatically
 * turn a resumable recovery-audit export into a terminal failure.
 */
public final class WarehouseRecoveryAuditPersistenceException extends RuntimeException {
    public WarehouseRecoveryAuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}