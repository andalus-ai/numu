package tech.kayys.numou.affiliate.application;

import java.util.Objects;

/** Security and retry context supplied by the host application; never infer tenant or actor from a public referral code. */
public record AffiliateCommandContext(
        String tenantId,
        String actorId,
        String idempotencyKey,
        String correlationId) {

    public AffiliateCommandContext {
        tenantId = required(tenantId, "tenantId");
        actorId = required(actorId, "actorId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        correlationId = required(correlationId, "correlationId");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
