package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record StudentStatusChanged(
        UUID eventId,
        Instant occurredAt,
        StudentId studentId,
        TenantId tenantId,
        String status
) implements DomainEvent {
    public StudentStatusChanged(StudentId studentId, TenantId tenantId, String status) {
        this(UUID.randomUUID(), Instant.now(), studentId, tenantId, status);
    }

    @Override
    public String eventType() {
        return "school.student.status-changed";
    }
}
