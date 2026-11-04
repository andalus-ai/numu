package tech.kayys.numou.talent.domain.profile.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfileId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Signals that one complete professional profile section was replaced. */
public record TalentProfileContentChanged(
        UUID eventId,
        Instant occurredAt,
        TalentProfileId profileId,
        TenantId tenantId,
        Section section
) implements DomainEvent {

    public enum Section { SKILLS, EXPERIENCES, EDUCATION, CREDENTIALS, PORTFOLIO }

    public TalentProfileContentChanged(TalentProfileId profileId, TenantId tenantId, Section section) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId, section);
    }

    public TalentProfileContentChanged {
        Objects.requireNonNull(eventId, "Event ID must not be null");
        Objects.requireNonNull(occurredAt, "Occurrence time must not be null");
        Objects.requireNonNull(profileId, "Profile ID must not be null");
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(section, "Changed section must not be null");
    }

    @Override
    public String eventType() {
        return "talent.profile.content-changed";
    }
}
