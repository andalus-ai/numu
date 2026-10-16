package tech.kayys.syirkah.tenancy.domain.provisioning.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.domain.provisioning.ProvisioningStep;
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
