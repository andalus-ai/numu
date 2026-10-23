package tech.kayys.numou.warehouse.application;

import java.time.Instant;
import java.util.Objects;

/** A recoverable execution journal entry temporarily claimed by one worker. */
public record WarehouseExecutionClaim(WarehouseExecutionAttempt attempt, String workerId, Instant leaseUntil) {
    public WarehouseExecutionClaim {
        Objects.requireNonNull(attempt, "attempt");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId required");
        workerId = workerId.trim();
        Objects.requireNonNull(leaseUntil, "leaseUntil");
    }
}
