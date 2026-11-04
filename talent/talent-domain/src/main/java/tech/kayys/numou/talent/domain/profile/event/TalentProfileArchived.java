package tech.kayys.numou.talent.domain.profile.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.time.Instant;
import java.util.UUID;

public record TalentProfileArchived(
        UUID eventId,
        Instant occurredAt,
        TalentProfileId profileId,
        TenantId tenantId
) implements DomainEvent {

    public TalentProfileArchived(TalentProfileId profileId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId);
    }

    @Override
    public String eventType() {
        return "talent.profile.archived";
    }
}
