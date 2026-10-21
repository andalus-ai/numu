package tech.kayys.numou.education.lms.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.QuizAttemptId;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record QuizAttempted(
        UUID eventId,
        Instant occurredAt,
        QuizAttemptId attemptId,
        TenantId tenantId,
        QuizId quizId,
        StudentId studentId,
        BigDecimal scorePercent
) implements DomainEvent {
    public QuizAttempted(QuizAttemptId attemptId, TenantId tenantId, QuizId quizId, StudentId studentId, BigDecimal scorePercent) {
        this(UUID.randomUUID(), Instant.now(), attemptId, tenantId, quizId, studentId, scorePercent);
    }

    @Override
    public String eventType() {
        return "lms.quiz.attempted";
    }
}
