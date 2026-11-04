package tech.kayys.numou.talent.domain.profile.event;

import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.time.Instant;
import java.util.UUID;

public record TalentProfilePublished(
        UUID eventId,
        Instant occurredAt,
        TalentProfileId profileId,
        TenantId tenantId,
        PartyRef subject
) implements DomainEvent {

    public TalentProfilePublished(TalentProfileId profileId, TenantId tenantId, PartyRef subject) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId, subject);
    }

    @Override
    public String eventType() {
        return "talent.profile.published";
    }
}
