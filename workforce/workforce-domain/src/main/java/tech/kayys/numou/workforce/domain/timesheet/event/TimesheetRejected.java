package tech.kayys.numou.workforce.domain.timesheet.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record TimesheetRejected(
        UUID eventId,
        Instant occurredAt,
        TimesheetId timesheetId,
        WorkerId workerId,
        String rejectedBy,
        String reason
) implements DomainEvent {
    public TimesheetRejected(TimesheetId id, WorkerId workerId, String rejectedBy, String reason) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, rejectedBy, reason);
    }
    @Override public String eventType() { return "workforce.timesheet.rejected"; }
}
