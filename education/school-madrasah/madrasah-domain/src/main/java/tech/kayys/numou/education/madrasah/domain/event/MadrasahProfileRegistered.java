package tech.kayys.numou.education.madrasah.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfileId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record MadrasahProfileRegistered(
        UUID eventId,
        Instant occurredAt,
        MadrasahProfileId profileId,
        TenantId tenantId,
        SchoolId schoolId,
        String nsm,
        EducationLevel level
) implements DomainEvent {
    public MadrasahProfileRegistered(MadrasahProfileId profileId, TenantId tenantId, SchoolId schoolId, String nsm, EducationLevel level) {
        this(UUID.randomUUID(), Instant.now(), profileId, tenantId, schoolId, nsm, level);
    }

    @Override
    public String eventType() {
        return "madrasah.profile.registered";
    }
}
