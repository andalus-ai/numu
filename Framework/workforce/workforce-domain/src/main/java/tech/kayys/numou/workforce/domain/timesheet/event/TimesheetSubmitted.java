package tech.kayys.numou.workforce.domain.timesheet.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record TimesheetSubmitted(
        UUID eventId,
        Instant occurredAt,
        TimesheetId timesheetId,
        WorkerId workerId,
        Duration totalDuration
) implements DomainEvent {
    public TimesheetSubmitted(TimesheetId id, WorkerId workerId, Duration totalDuration) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, totalDuration);
    }
    @Override public String eventType() { return "workforce.timesheet.submitted"; }
}
