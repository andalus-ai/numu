package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceFeedbackRecorded;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

public final class PerformanceFeedback extends AbstractAggregateRoot<PerformanceFeedbackId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final WorkerId authorWorkerId;
    private final PerformanceCycleId cycleId;
    private final FeedbackType type;
    private final String content;
    private final Instant recordedAt;

    private PerformanceFeedback(
            PerformanceFeedbackId id,
            WorkerId workerId,
            EmploymentId employmentId,
            WorkerId authorWorkerId,
            PerformanceCycleId cycleId,
            FeedbackType type,
            String content,
            Instant recordedAt
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.authorWorkerId = Objects.requireNonNull(authorWorkerId, "authorWorkerId must not be null");
        this.cycleId = cycleId;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    public static PerformanceFeedback create(
            PerformanceFeedbackId id,
            WorkerId workerId,
            EmploymentId employmentId,
            WorkerId authorWorkerId,
            PerformanceCycleId cycleId,
            FeedbackType type,
            String content,
            Instant recordedAt
    ) {
        PerformanceFeedback fb = new PerformanceFeedback(id, workerId, employmentId, authorWorkerId, cycleId, type, content, recordedAt);
        fb.raise(new PerformanceFeedbackRecorded(id, workerId, type));
        return fb;
    }

    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public WorkerId getAuthorWorkerId() { return authorWorkerId; }
    public PerformanceCycleId getCycleId() { return cycleId; }
    public FeedbackType getType() { return type; }
    public String getContent() { return content; }
    public Instant getRecordedAt() { return recordedAt; }
}
