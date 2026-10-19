package tech.kayys.numou.tenancy.domain.provisioning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningCompleted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.provisioning.completed"; }
}
