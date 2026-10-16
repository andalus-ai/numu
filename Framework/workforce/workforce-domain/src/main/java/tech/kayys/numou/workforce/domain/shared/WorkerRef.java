package tech.kayys.numou.workforce.domain.shared;

import tech.kayys.numou.workforce.domain.worker.WorkerId;
import java.util.Objects;

/**
 * Lightweight reference to a Worker across domain boundaries (C-01 §5).
 */
public record WorkerRef(WorkerId workerId) {
    public WorkerRef {
        Objects.requireNonNull(workerId, "workerId must not be null");
    }
}
