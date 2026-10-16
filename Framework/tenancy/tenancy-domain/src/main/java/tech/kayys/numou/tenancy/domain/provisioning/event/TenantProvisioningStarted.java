package tech.kayys.syirkah.tenancy.domain.provisioning.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningStarted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.provisioning.started"; }
}
