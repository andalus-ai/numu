package tech.kayys.numou.workforce.domain.attendance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record AttendanceRecordClosed(
        UUID eventId,
        Instant occurredAt,
        AttendanceRecordId attendanceRecordId,
        WorkerId workerId,
        EmploymentId employmentId,
        Duration totalDuration
) implements DomainEvent {
    public AttendanceRecordClosed(AttendanceRecordId id, WorkerId workerId, EmploymentId employmentId, Duration totalDuration) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, employmentId, totalDuration);
    }
    @Override public String eventType() { return "workforce.attendance.record-closed"; }
}
