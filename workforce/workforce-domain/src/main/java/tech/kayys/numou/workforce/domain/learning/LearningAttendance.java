package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.learning.event.LearningAttendanceRecorded;

import java.time.Instant;
import java.util.Objects;

public final class LearningAttendance extends AbstractAggregateRoot<LearningAttendanceId> {

    private final LearningEnrollmentId enrollmentId;
    private final LearningSessionId sessionId;
    private LearningAttendanceStatus status;
    private final Instant recordedAt;

    private LearningAttendance(
            LearningAttendanceId id,
            LearningEnrollmentId enrollmentId,
            LearningSessionId sessionId,
            LearningAttendanceStatus status,
            Instant recordedAt
    ) {
        super(id);
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    public static LearningAttendance record(
            LearningAttendanceId id,
            LearningEnrollmentId enrollmentId,
            LearningSessionId sessionId,
            LearningAttendanceStatus status,
            Instant recordedAt
    ) {
        LearningAttendance att = new LearningAttendance(id, enrollmentId, sessionId, status, recordedAt);
        att.raise(new LearningAttendanceRecorded(id, enrollmentId, sessionId, status));
        return att;
    }

    public LearningEnrollmentId getEnrollmentId() { return enrollmentId; }
    public LearningSessionId getSessionId() { return sessionId; }
    public LearningAttendanceStatus getStatus() { return status; }
    public Instant getRecordedAt() { return recordedAt; }
}
