package tech.kayys.numou.tenancy.domain.provisioning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.domain.provisioning.ProvisioningStep;
import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningFailed(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        ProvisioningStep step,
        String reason
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.provisioning.failed"; }
}
