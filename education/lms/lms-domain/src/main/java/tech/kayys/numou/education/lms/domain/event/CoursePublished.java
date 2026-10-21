package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record CoursePublished(
        UUID eventId,
        Instant occurredAt,
        CourseId courseId,
        TenantId tenantId
) implements DomainEvent {
    public CoursePublished(CourseId courseId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), courseId, tenantId);
    }

    @Override
    public String eventType() {
        return "lms.course.published";
    }
}
