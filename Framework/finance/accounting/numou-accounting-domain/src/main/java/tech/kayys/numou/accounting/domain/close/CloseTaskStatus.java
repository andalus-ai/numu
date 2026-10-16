package tech.kayys.syirkah.accounting.domain.close;

/**
 * Execution status of an individual close task.
 */
public enum CloseTaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED
}
