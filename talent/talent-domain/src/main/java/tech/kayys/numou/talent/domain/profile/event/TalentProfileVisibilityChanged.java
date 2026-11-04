package tech.kayys.numou.talent.domain.profile.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.ProfileVisibility;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.time.Instant;
import java.util.UUID;

public record TalentProfileVisibilityChanged(
        UUID eventId,
        Instant occurredAt,
        TalentProfileId profileId,
        TenantId tenantId,
        ProfileVisibility previousVisibility,
        ProfileVisibility visibility
) implements DomainEvent {

    public TalentProfileVisibilityChanged(
            TalentProfileId profileId,
            TenantId tenantId,
            ProfileVisibility previousVisibility,
            ProfileVisibility visibility
    ) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId, previousVisibility, visibility);
    }

    @Override
    public String eventType() {
        return "talent.profile.visibility-changed";
    }
}
