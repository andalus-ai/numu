package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.education.lms.domain.event.AssignmentPublished;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** A graded task attached to a course. */
public final class Assignment extends AbstractAggregateRoot<AssignmentId> {

    private final TenantId tenantId;
    private final CourseId courseId;
    private final String title;
    private final String instructions;
    private final Instant dueAt;
    private final int maxScore;
    private final boolean allowLate;
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    private Assignment(AssignmentId id, TenantId tenantId, CourseId courseId, String title, String instructions,
                       Instant dueAt, int maxScore, boolean allowLate) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Assignment title must not be blank");
        this.title = title.trim();
        this.instructions = instructions;
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt must not be null");
        if (maxScore <= 0) throw new IllegalArgumentException("maxScore must be positive");
        this.maxScore = maxScore;
        this.allowLate = allowLate;
    }

    public static Assignment create(AssignmentId id, TenantId tenantId, CourseId courseId, String title,
                                    String instructions, Instant dueAt, int maxScore, boolean allowLate) {
        return new Assignment(id, tenantId, courseId, title, instructions, dueAt, maxScore, allowLate);
    }

    public void publish() {
        if (status != AssignmentStatus.DRAFT) throw new IllegalStateException("Only DRAFT assignments can be published");
        status = AssignmentStatus.PUBLISHED;
        touch();
        raise(new AssignmentPublished(getId(), tenantId, courseId));
    }

    public void close() {
        status = AssignmentStatus.CLOSED;
        touch();
    }

    public boolean acceptsSubmissionAt(Instant now) {
        return status == AssignmentStatus.PUBLISHED && (allowLate || !now.isAfter(dueAt));
    }

    public boolean isLate(Instant now) { return now.isAfter(dueAt); }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public CourseId getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public String getInstructions() { return instructions; }
    public Instant getDueAt() { return dueAt; }
    public int getMaxScore() { return maxScore; }
    public boolean isAllowLate() { return allowLate; }
    public AssignmentStatus getStatus() { return status; }
}
