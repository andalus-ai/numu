package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SchoolRegistered(
        UUID eventId,
        Instant occurredAt,
        SchoolId schoolId,
        TenantId tenantId,
        String npsn,
        String name,
        EducationLevel level
) implements DomainEvent {
    public SchoolRegistered(SchoolId schoolId, TenantId tenantId, String npsn, String name, EducationLevel level) {
        this(UUID.randomUUID(), Instant.now(), schoolId, tenantId, npsn, name, level);
    }

    @Override
    public String eventType() {
        return "school.school.registered";
    }
}
