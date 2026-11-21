package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;

import java.time.Instant;
import java.util.Objects;

/** Tenant-scoped replay identity for directed relationship commands. */
public record SocialRelationshipIdempotencyRecord(TenantId tenantId, String key,
        String fingerprint, SocialRelationshipId relationshipId, Instant createdAt) {
    public SocialRelationshipIdempotencyRecord {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (key == null || key.isBlank() || key.length() > 128)
            throw new IllegalArgumentException("Invalid idempotency key");
        if (fingerprint == null || fingerprint.isBlank() || fingerprint.length() > 256)
            throw new IllegalArgumentException("Invalid command fingerprint");
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }
}
