package tech.kayys.numou.identity.domain.role;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record RolePermissionChanged(
        UUID eventId,
        Instant occurredAt,
        RoleId roleId,
        TenantId tenantId,
        Permission permission,
        boolean granted
) implements DomainEvent {
    @Override
    public String eventType() {
        return granted ? "identity.role-permission-granted" : "identity.role-permission-revoked";
    }
}
