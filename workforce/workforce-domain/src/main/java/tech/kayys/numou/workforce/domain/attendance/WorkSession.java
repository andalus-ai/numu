package tech.kayys.numou.workforce.domain.attendance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class WorkSession {

    private final WorkSessionId id;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private WorkSessionStatus status;
    private final AttendanceSource source;

    private WorkSession(WorkSessionId id, LocalDateTime startTime, AttendanceSource source) {
        this.id = Objects.requireNonNull(id, "WorkSessionId must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.source = source != null ? source : AttendanceSource.WEB;
        this.status = WorkSessionStatus.OPEN;
    }

    public static WorkSession open(LocalDateTime startTime, AttendanceSource source) {
        return new WorkSession(WorkSessionId.generate(), startTime, source);
    }

    public static WorkSession open(LocalDateTime startTime) {
        return open(startTime, AttendanceSource.WEB);
    }

    public void close(LocalDateTime endTime) {
        if (status != WorkSessionStatus.OPEN) {
            throw new IllegalStateException("Work session is not open");
        }
        Objects.requireNonNull(endTime, "endTime must not be null");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        this.endTime = endTime;
        this.status = WorkSessionStatus.CLOSED;
    }

    public boolean isOpen() {
        return status == WorkSessionStatus.OPEN;
    }

    public Duration duration() {
        if (endTime == null) {
            throw new IllegalStateException("Open session has no duration");
        }
        return Duration.between(startTime, endTime);
    }

    public WorkSessionId getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public WorkSessionStatus getStatus() { return status; }
    public AttendanceSource getSource() { return source; }
}
