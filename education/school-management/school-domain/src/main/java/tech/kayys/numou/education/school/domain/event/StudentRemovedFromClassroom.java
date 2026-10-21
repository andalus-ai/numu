package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record StudentRemovedFromClassroom(
        UUID eventId,
        Instant occurredAt,
        ClassroomId classroomId,
        TenantId tenantId,
        StudentId studentId
) implements DomainEvent {
    public StudentRemovedFromClassroom(ClassroomId classroomId, TenantId tenantId, StudentId studentId) {
        this(UUID.randomUUID(), Instant.now(), classroomId, tenantId, studentId);
    }

    @Override
    public String eventType() {
        return "school.classroom.student-removed";
    }
}
