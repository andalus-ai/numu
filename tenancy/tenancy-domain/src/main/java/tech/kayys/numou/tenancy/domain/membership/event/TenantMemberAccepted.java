package tech.kayys.numou.tenancy.domain.membership.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.ref.UserRef;
import tech.kayys.numou.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

public record TenantMemberAccepted(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        TenantMembershipId membershipId,
        UserRef user
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.membership.accepted"; }
}
