package tech.kayys.numou.workforce.domain.timesheet.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetEntryId;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.timesheet.WorkContextRef;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryAdded(
        UUID eventId,
        Instant occurredAt,
        TimesheetId timesheetId,
        TimesheetEntryId entryId,
        LocalDate date,
        Duration duration,
        WorkContextRef context
) implements DomainEvent {
    public TimesheetEntryAdded(TimesheetId id, TimesheetEntryId entryId, LocalDate date,
                               Duration duration, WorkContextRef context) {
        this(UUID.randomUUID(), Instant.now(), id, entryId, date, duration, context);
    }
    @Override public String eventType() { return "workforce.timesheet.entry-added"; }
}
