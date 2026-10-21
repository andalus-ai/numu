package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record CourseCreated(
        UUID eventId,
        Instant occurredAt,
        CourseId courseId,
        TenantId tenantId,
        String title
) implements DomainEvent {
    public CourseCreated(CourseId courseId, TenantId tenantId, String title) {
        this(UUID.randomUUID(), Instant.now(), courseId, tenantId, title);
    }

    @Override
    public String eventType() {
        return "lms.course.created";
    }
}
