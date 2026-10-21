package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record AssignmentPublished(
        UUID eventId,
        Instant occurredAt,
        AssignmentId assignmentId,
        TenantId tenantId,
        CourseId courseId
) implements DomainEvent {
    public AssignmentPublished(AssignmentId assignmentId, TenantId tenantId, CourseId courseId) {
        this(UUID.randomUUID(), Instant.now(), assignmentId, tenantId, courseId);
    }

    @Override
    public String eventType() {
        return "lms.assignment.published";
    }
}
