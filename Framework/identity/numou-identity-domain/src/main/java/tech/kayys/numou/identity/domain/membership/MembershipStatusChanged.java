package tech.kayys.syirkah.identity.domain.membership;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record MembershipStatusChanged(
        UUID eventId,
        Instant occurredAt,
        MembershipId membershipId,
        TenantId tenantId,
        UserId userId,
        MembershipStatus status,
        String reason
) implements DomainEvent {
    @Override
    public String eventType() {
        return "identity.membership-status-changed";
    }
}
