package tech.kayys.syirkah.tenancy.domain.provisioning.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.domain.provisioning.ProvisioningStep;
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
