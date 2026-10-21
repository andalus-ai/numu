package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.event.AssignmentSubmitted;
import tech.kayys.numou.education.lms.domain.event.SubmissionGraded;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/** A student's work for an assignment, with grading outcome. */
public final class Submission extends AbstractAggregateRoot<SubmissionId> {

    private final TenantId tenantId;
    private final AssignmentId assignmentId;
    private final StudentId studentId;
    private String content;
    private Instant submittedAt;
    private boolean late;
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;
    private BigDecimal score;
    private String feedback;

    private Submission(SubmissionId id, TenantId tenantId, AssignmentId assignmentId, StudentId studentId) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    }

    public static Submission submit(SubmissionId id, TenantId tenantId, Assignment assignment, StudentId studentId,
                                    String content, Instant now) {
        requireAccepting(assignment, now);
        Submission s = new Submission(id, tenantId, assignment.getId(), studentId);
        s.apply(assignment, content, now);
        s.raise(new AssignmentSubmitted(id, tenantId, assignment.getId(), studentId, s.late));
        return s;
    }

    /** Replace the work. Allowed until it has been graded, or after it was returned for revision. */
    public void resubmit(Assignment assignment, String newContent, Instant now) {
        if (status == SubmissionStatus.GRADED) throw new IllegalStateException("Graded submissions cannot be changed");
        requireAccepting(assignment, now);
        apply(assignment, newContent, now);
        status = SubmissionStatus.SUBMITTED;
        feedback = null;
        raise(new AssignmentSubmitted(getId(), tenantId, assignmentId, studentId, late));
    }

    public void grade(BigDecimal newScore, int maxScore, String newFeedback) {
        Objects.requireNonNull(newScore, "score must not be null");
        if (status != SubmissionStatus.SUBMITTED) throw new IllegalStateException("Only SUBMITTED work can be graded");
        if (newScore.signum() < 0 || newScore.compareTo(BigDecimal.valueOf(maxScore)) > 0) {
            throw new IllegalArgumentException("Score must be between 0 and " + maxScore);
        }
        this.score = newScore.setScale(2, RoundingMode.HALF_UP);
        this.feedback = newFeedback;
        this.status = SubmissionStatus.GRADED;
        touch();
        raise(new SubmissionGraded(getId(), tenantId, studentId, this.score));
    }

    public void returnForRevision(String newFeedback) {
        if (status != SubmissionStatus.SUBMITTED) throw new IllegalStateException("Only SUBMITTED work can be returned");
        this.feedback = newFeedback;
        this.status = SubmissionStatus.RETURNED;
        touch();
    }

    private static void requireAccepting(Assignment a, Instant now) {
        if (!a.acceptsSubmissionAt(now)) throw new IllegalStateException("Assignment is not accepting submissions");
    }

    private void apply(Assignment a, String newContent, Instant now) {
        if (newContent == null || newContent.isBlank()) throw new IllegalArgumentException("Submission content must not be blank");
        this.content = newContent;
        this.submittedAt = now;
        this.late = a.isLate(now);
        touch();
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public AssignmentId getAssignmentId() { return assignmentId; }
    public StudentId getStudentId() { return studentId; }
    public String getContent() { return content; }
    public Instant getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
    public SubmissionStatus getStatus() { return status; }
    public BigDecimal getScore() { return score; }
    public String getFeedback() { return feedback; }
}
