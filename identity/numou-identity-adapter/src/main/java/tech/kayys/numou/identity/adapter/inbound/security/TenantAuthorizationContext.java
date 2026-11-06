package tech.kayys.numou.identity.adapter.inbound.security;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.UUID;

/** Authenticated actor and verified tenant returned after tenant authorization. */
public record TenantAuthorizationContext(UUID actorId, TenantId tenantId) {
    public TenantAuthorizationContext {
        Objects.requireNonNull(actorId, "actorId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
    }
}
