package tech.kayys.numou.history.source.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A time-bounded, fenced claim on one ingestion request.
 * The token changes on every claim; workers must present it for every state transition.
 */
public record SourceIngestionLease(
        UUID ingestionId,
        String workerRef,
        UUID leaseToken,
        int attempt,
        Instant leasedAt,
        Instant leaseUntil
) {
    public SourceIngestionLease {
        Objects.requireNonNull(ingestionId, "ingestionId");
        workerRef = required(workerRef, "workerRef", 256);
        Objects.requireNonNull(leaseToken, "leaseToken");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        Objects.requireNonNull(leasedAt, "leasedAt");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(leasedAt)) {
            throw new IllegalArgumentException("leaseUntil must be after leasedAt");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }
}
