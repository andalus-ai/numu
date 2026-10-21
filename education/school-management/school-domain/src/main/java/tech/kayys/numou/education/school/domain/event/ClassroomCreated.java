package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ClassroomCreated(
        UUID eventId,
        Instant occurredAt,
        ClassroomId classroomId,
        TenantId tenantId,
        SchoolId schoolId,
        String name
) implements DomainEvent {
    public ClassroomCreated(ClassroomId classroomId, TenantId tenantId, SchoolId schoolId, String name) {
        this(UUID.randomUUID(), Instant.now(), classroomId, tenantId, schoolId, name);
    }

    @Override
    public String eventType() {
        return "school.classroom.created";
    }
}
