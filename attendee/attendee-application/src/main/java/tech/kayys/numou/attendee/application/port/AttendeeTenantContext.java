package tech.kayys.numou.attendee.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Trusted request identity established by authentication and tenant-membership
 * resolution at the inbound adapter. Never populate this from request JSON.
 */
public record AttendeeTenantContext(TenantId tenantId, UUID actorId) {
    public AttendeeTenantContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(actorId, "actorId cannot be null");
    }
}
