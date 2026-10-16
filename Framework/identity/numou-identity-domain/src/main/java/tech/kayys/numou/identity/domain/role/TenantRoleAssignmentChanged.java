package tech.kayys.numou.identity.domain.role;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record TenantRoleAssignmentChanged(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        UserId userId,
        RoleId roleId,
        UserId changedBy,
        boolean assigned
) implements DomainEvent {
    @Override
    public String eventType() {
        return assigned ? "identity.role-assigned" : "identity.role-removed";
    }
}
