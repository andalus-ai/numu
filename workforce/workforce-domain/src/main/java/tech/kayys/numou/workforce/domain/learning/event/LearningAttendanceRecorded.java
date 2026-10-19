package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.learning.LearningAttendanceId;
import tech.kayys.numou.workforce.domain.learning.LearningAttendanceStatus;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;
import tech.kayys.numou.workforce.domain.learning.LearningSessionId;

import java.time.Instant;
import java.util.UUID;

public record LearningAttendanceRecorded(
        UUID eventId,
        Instant occurredAt,
        LearningAttendanceId id,
        LearningEnrollmentId enrollmentId,
        LearningSessionId sessionId,
        LearningAttendanceStatus status
) implements DomainEvent {
    public LearningAttendanceRecorded(LearningAttendanceId id, LearningEnrollmentId enrollmentId, LearningSessionId sessionId, LearningAttendanceStatus status) {
        this(UUID.randomUUID(), Instant.now(), id, enrollmentId, sessionId, status);
    }
    @Override public String eventType() { return "workforce.learning.attendance.recorded"; }
}
