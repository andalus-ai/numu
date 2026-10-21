package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record TeacherRegistered(
        UUID eventId,
        Instant occurredAt,
        TeacherId teacherId,
        TenantId tenantId,
        SchoolId schoolId,
        String fullName
) implements DomainEvent {
    public TeacherRegistered(TeacherId teacherId, TenantId tenantId, SchoolId schoolId, String fullName) {
        this(UUID.randomUUID(), Instant.now(), teacherId, tenantId, schoolId, fullName);
    }

    @Override
    public String eventType() {
        return "school.teacher.registered";
    }
}
