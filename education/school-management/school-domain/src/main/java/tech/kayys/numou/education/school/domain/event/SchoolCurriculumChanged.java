package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.Curriculum;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SchoolCurriculumChanged(
        UUID eventId,
        Instant occurredAt,
        SchoolId schoolId,
        TenantId tenantId,
        Curriculum curriculum
) implements DomainEvent {
    public SchoolCurriculumChanged(SchoolId schoolId, TenantId tenantId, Curriculum curriculum) {
        this(UUID.randomUUID(), Instant.now(), schoolId, tenantId, curriculum);
    }

    @Override
    public String eventType() {
        return "school.school.curriculum-changed";
    }
}
