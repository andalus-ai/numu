package tech.kayys.numou.workforce.domain.attendance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceRecordCreated(
        UUID eventId,
        Instant occurredAt,
        AttendanceRecordId attendanceRecordId,
        WorkerId workerId,
        EmploymentId employmentId,
        LocalDate workDate
) implements DomainEvent {
    public AttendanceRecordCreated(AttendanceRecordId id, WorkerId workerId, EmploymentId employmentId, LocalDate workDate) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, employmentId, workDate);
    }
    @Override public String eventType() { return "workforce.attendance.record-created"; }
}
