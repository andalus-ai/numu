package tech.kayys.numou.talent.domain.profile.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.time.Instant;
import java.util.UUID;

/** Signals a presentation update without embedding potentially private profile text in the event. */
public record TalentProfilePresentationChanged(
        UUID eventId,
        Instant occurredAt,
        TalentProfileId profileId,
        TenantId tenantId
) implements DomainEvent {

    public TalentProfilePresentationChanged(TalentProfileId profileId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId);
    }

    @Override
    public String eventType() {
        return "talent.profile.presentation-changed";
    }
}
