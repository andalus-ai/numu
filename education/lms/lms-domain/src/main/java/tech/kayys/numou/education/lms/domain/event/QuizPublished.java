package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record QuizPublished(
        UUID eventId,
        Instant occurredAt,
        QuizId quizId,
        TenantId tenantId,
        CourseId courseId
) implements DomainEvent {
    public QuizPublished(QuizId quizId, TenantId tenantId, CourseId courseId) {
        this(UUID.randomUUID(), Instant.now(), quizId, tenantId, courseId);
    }

    @Override
    public String eventType() {
        return "lms.quiz.published";
    }
}
