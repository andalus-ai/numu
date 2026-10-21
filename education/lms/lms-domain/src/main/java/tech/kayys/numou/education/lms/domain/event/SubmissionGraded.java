package tech.kayys.numou.education.lms.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SubmissionGraded(
        UUID eventId,
        Instant occurredAt,
        SubmissionId submissionId,
        TenantId tenantId,
        StudentId studentId,
        BigDecimal score
) implements DomainEvent {
    public SubmissionGraded(SubmissionId submissionId, TenantId tenantId, StudentId studentId, BigDecimal score) {
        this(UUID.randomUUID(), Instant.now(), submissionId, tenantId, studentId, score);
    }

    @Override
    public String eventType() {
        return "lms.submission.graded";
    }
}
