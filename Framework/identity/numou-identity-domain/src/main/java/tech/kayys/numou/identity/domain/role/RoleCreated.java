package tech.kayys.syirkah.identity.domain.role;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record RoleCreated(
        UUID eventId,
        Instant occurredAt,
        RoleId roleId,
        TenantId tenantId,
        String code,
        String name,
        boolean builtIn
) implements DomainEvent {
    @Override
    public String eventType() {
        return "identity.role-created";
    }
}
