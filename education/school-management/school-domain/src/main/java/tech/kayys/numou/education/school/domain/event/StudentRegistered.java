package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record StudentRegistered(
        UUID eventId,
        Instant occurredAt,
        StudentId studentId,
        TenantId tenantId,
        SchoolId schoolId,
        String fullName
) implements DomainEvent {
    public StudentRegistered(StudentId studentId, TenantId tenantId, SchoolId schoolId, String fullName) {
        this(UUID.randomUUID(), Instant.now(), studentId, tenantId, schoolId, fullName);
    }

    @Override
    public String eventType() {
        return "school.student.registered";
    }
}
