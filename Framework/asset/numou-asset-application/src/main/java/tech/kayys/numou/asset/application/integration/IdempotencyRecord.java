package tech.kayys.numou.asset.application.integration;

import java.time.Instant;
import java.util.Objects;

/**
 * A stored idempotency key for an external write (ASSET-28 §47).
 *
 * <p>{@code requestFingerprint} lets the store detect a same-key / different-body
 * conflict instead of silently replaying the first result.</p>
 */
public record IdempotencyRecord(
        String tenantId,
        String key,
        String operation,
        String requestFingerprint,
        Instant storedAt
) {

    public IdempotencyRecord {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(storedAt, "storedAt cannot be null");
    }
}