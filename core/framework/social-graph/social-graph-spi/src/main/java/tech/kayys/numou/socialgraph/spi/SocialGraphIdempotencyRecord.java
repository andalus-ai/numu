package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.time.Instant;
import java.util.Objects;

/** Stored result identity for a tenant-scoped idempotent command. */
public record SocialGraphIdempotencyRecord(TenantId tenantId, String key,
        String fingerprint, SocialConnectionId connectionId, Instant createdAt) {
    public SocialGraphIdempotencyRecord {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (key == null || key.isBlank() || key.length() > 128) throw new IllegalArgumentException("Invalid idempotency key");
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        Objects.requireNonNull(connectionId, "connectionId cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }
}
