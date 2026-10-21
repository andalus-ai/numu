package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record StudentEnrolledInCourse(
        UUID eventId,
        Instant occurredAt,
        CourseId courseId,
        TenantId tenantId,
        StudentId studentId
) implements DomainEvent {
    public StudentEnrolledInCourse(CourseId courseId, TenantId tenantId, StudentId studentId) {
        this(UUID.randomUUID(), Instant.now(), courseId, tenantId, studentId);
    }

    @Override
    public String eventType() {
        return "lms.course.student-enrolled";
    }
}
