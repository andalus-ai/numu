package tech.kayys.numou.tenancy.domain.provisioning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.domain.provisioning.ProvisioningStep;
import java.time.Instant;
import java.util.UUID;

public record TenantProvisioningStepCompleted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        ProvisioningStep step
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.provisioning.step_completed"; }
}
