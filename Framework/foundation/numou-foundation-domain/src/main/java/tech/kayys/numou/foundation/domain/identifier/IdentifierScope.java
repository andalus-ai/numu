package tech.kayys.numou.foundation.domain.identifier;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant and optional business dimensions that define the uniqueness boundary for numbering (config03.md §P4-15 #5).
 */
public record IdentifierScope(
        TenantId tenantId,
        Optional<UUID> legalEntityId,
        Optional<UUID> businessUnitId
) implements Serializable {

    public IdentifierScope {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId cannot be null");
        businessUnitId = Objects.requireNonNull(businessUnitId, "businessUnitId cannot be null");
    }

    public static IdentifierScope ofTenant(TenantId tenantId) {
        return new IdentifierScope(tenantId, Optional.empty(), Optional.empty());
    }

    public static IdentifierScope ofLegalEntity(TenantId tenantId, UUID legalEntityId) {
        return new IdentifierScope(tenantId, Optional.ofNullable(legalEntityId), Optional.empty());
    }

    public static IdentifierScope ofBusinessUnit(TenantId tenantId, UUID legalEntityId, UUID businessUnitId) {
        return new IdentifierScope(tenantId, Optional.ofNullable(legalEntityId), Optional.ofNullable(businessUnitId));
    }

    /**
     * Produces an unambiguous canonical scope key for sequence allocation.
     */
    public String toScopeKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("tenant:").append(tenantId.value());
        legalEntityId.ifPresent(le -> sb.append(":le:").append(le));
        businessUnitId.ifPresent(bu -> sb.append(":bu:").append(bu));
        return sb.toString();
    }
}
