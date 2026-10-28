package tech.kayys.numou.affiliate.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable policy snapshot; policy edits create a new version. */
public record AttributionPolicy(UUID id, String tenantId, UUID programId, long version,
        AttributionModel model, long windowSeconds, Instant effectiveFrom, Instant createdAt) {
    public AttributionPolicy {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        if (version < 1) throw new IllegalArgumentException("policy version must be positive");
        Objects.requireNonNull(model, "model is required");
        if (windowSeconds < 1) throw new IllegalArgumentException("windowSeconds must be positive");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (effectiveFrom.isBefore(createdAt)) throw new IllegalArgumentException("effectiveFrom cannot precede creation");
    }
    public boolean isEffectiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        return !instant.isBefore(effectiveFrom);
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String v = value.trim();
        if (v.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}
