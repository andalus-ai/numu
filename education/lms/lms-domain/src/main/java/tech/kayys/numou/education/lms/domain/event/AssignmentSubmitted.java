package tech.kayys.numou.education.lms.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record AssignmentSubmitted(
        UUID eventId,
        Instant occurredAt,
        SubmissionId submissionId,
        TenantId tenantId,
        AssignmentId assignmentId,
        StudentId studentId,
        boolean late
) implements DomainEvent {
    public AssignmentSubmitted(SubmissionId submissionId, TenantId tenantId, AssignmentId assignmentId, StudentId studentId, boolean late) {
        this(UUID.randomUUID(), Instant.now(), submissionId, tenantId, assignmentId, studentId, late);
    }

    @Override
    public String eventType() {
        return "lms.submission.submitted";
    }
}
