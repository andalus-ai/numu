package tech.kayys.numou.workforce.domain.attendance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.attendance.event.AttendanceRecordClosed;
import tech.kayys.numou.workforce.domain.attendance.event.AttendanceRecordCreated;
import tech.kayys.numou.workforce.domain.attendance.event.WorkerClockedIn;
import tech.kayys.numou.workforce.domain.attendance.event.WorkerClockedOut;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AttendanceRecord aggregate root — records actual attendance observations for a worker.
 *
 * <p>Scoped by {@code WorkerId + EmploymentId + WorkDate}.
 * <p>Enforces:
 * <ul>
 *   <li>Only one open work session at a time</li>
 *   <li>Cannot clock out without an open session</li>
 *   <li>Cannot close attendance while a session is open</li>
 * </ul>
 */
public final class AttendanceRecord extends AbstractAggregateRoot<AttendanceRecordId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final LocalDate workDate;
    private AttendanceStatus status;
    private final List<WorkSession> sessions;

    private AttendanceRecord(AttendanceRecordId id, WorkerId workerId, EmploymentId employmentId, LocalDate workDate) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.workDate = Objects.requireNonNull(workDate, "workDate must not be null");
        this.status = AttendanceStatus.OPEN;
        this.sessions = new ArrayList<>();
    }

    public static AttendanceRecord create(AttendanceRecordId id, WorkerId workerId, EmploymentId employmentId, LocalDate workDate) {
        AttendanceRecord record = new AttendanceRecord(id, workerId, employmentId, workDate);
        record.raise(new AttendanceRecordCreated(id, workerId, employmentId, workDate));
        return record;
    }

    public void clockIn(LocalDateTime timestamp, AttendanceSource source) {
        if (status != AttendanceStatus.OPEN) {
            throw new IllegalStateException("Attendance record is not open");
        }
        if (hasOpenSession()) {
            throw new IllegalStateException("Worker already has an open work session");
        }
        WorkSession session = WorkSession.open(timestamp, source);
        sessions.add(session);
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkerClockedIn(getId(), workerId, employmentId, timestamp, source));
    }

    public void clockIn(LocalDateTime timestamp) {
        clockIn(timestamp, AttendanceSource.WEB);
    }

    public void clockOut(LocalDateTime timestamp) {
        if (status != AttendanceStatus.OPEN) {
            throw new IllegalStateException("Attendance record is not open");
        }
        WorkSession session = openSession();
        session.close(timestamp);
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkerClockedOut(getId(), workerId, employmentId, timestamp));
    }

    public void close() {
        if (hasOpenSession()) {
            throw new IllegalStateException("Cannot close attendance with an open session");
        }
        if (status == AttendanceStatus.CLOSED) {
            return;
        }
        this.status = AttendanceStatus.CLOSED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new AttendanceRecordClosed(getId(), workerId, employmentId, totalDuration()));
    }

    public Duration totalDuration() {
        return sessions.stream()
                .filter(s -> !s.isOpen())
                .map(WorkSession::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    public boolean hasOpenSession() {
        return sessions.stream().anyMatch(WorkSession::isOpen);
    }

    private WorkSession openSession() {
        return sessions.stream()
                .filter(WorkSession::isOpen)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No open work session found"));
    }

    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public LocalDate getWorkDate() { return workDate; }
    public AttendanceStatus getStatus() { return status; }
    public List<WorkSession> getSessions() { return Collections.unmodifiableList(sessions); }
}
