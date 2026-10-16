package tech.kayys.syirkah.security.domain.authorization;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;

import java.util.*;

/**
 * Who is asking (base01.md §P1-17, security01.md §3.2).
 *
 * <p>A principal may belong to several tenants, so tenancy is not a
 * property of the principal - it is part of the request being authorised.
 *
 * @param id        principal identity
 * @param handle    login / service name for human-readable audit
 * @param tenantIds tenants this principal is a member of
 */
public record Principal(
        PrincipalId id,
        String handle,
        Set<TenantId> tenantIds) {

    public Principal {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(handle, "handle cannot be null");
        tenantIds = tenantIds == null ? Set.of() : Set.copyOf(tenantIds);
    }

    /** True when this principal is a member of the given tenant. */
    public boolean isMemberOf(TenantId tenantId) {
        return tenantId != null && tenantIds.contains(tenantId);
    }

    /** Backwards-compatible overload for UUID tenantId. */
    public boolean isMemberOf(UUID tenantId) {
        return tenantId != null && isMemberOf(TenantId.of(tenantId));
    }

    /** True if this is a platform-level principal (not scoped to any single tenant). */
    public boolean isPlatformPrincipal() {
        return tenantIds.isEmpty();
    }

    /** Convenience builder for a single-tenant principal with TenantId. */
    public static Principal of(PrincipalId id, String handle, TenantId tenantId) {
        final var tenants = new LinkedHashSet<TenantId>();
        if (tenantId != null) {
            tenants.add(tenantId);
        }
        return new Principal(id, handle, Collections.unmodifiableSet(tenants));
    }

    /** Convenience builder for a single-tenant principal with UUID. */
    public static Principal of(PrincipalId id, String handle, UUID tenantId) {
        return of(id, handle, tenantId != null ? TenantId.of(tenantId) : null);
    }
}
