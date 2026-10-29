package tech.kayys.numou.pos.application;

import java.util.Objects;
import java.util.UUID;

/** Trusted context created at the authenticated application boundary. */
public record SaleCommandContext(UUID tenantId, UUID actorId, String correlationId) {
    public SaleCommandContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        if (correlationId.isBlank()) throw new IllegalArgumentException("correlationId cannot be blank");
        correlationId = correlationId.trim();
    }
}
