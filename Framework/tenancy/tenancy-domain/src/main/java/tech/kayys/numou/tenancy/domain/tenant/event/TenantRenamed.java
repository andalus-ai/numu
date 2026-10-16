package tech.kayys.syirkah.tenancy.domain.tenant.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Raised when a Tenant display name is changed. */
public record TenantRenamed(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String previousName,
        String newName
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.tenant.renamed"; }
}
