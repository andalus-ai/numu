package tech.kayys.numou.workforce.domain.worker;

/**
 * Worker lifecycle status.
 */
public enum WorkerStatus {
    ACTIVE,
    SUSPENDED,
    INACTIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
