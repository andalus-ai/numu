package tech.kayys.syirkah.tenancy.domain.membership.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantMemberRemoved(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        TenantMembershipId membershipId,
        UserRef user
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.membership.removed"; }
}
