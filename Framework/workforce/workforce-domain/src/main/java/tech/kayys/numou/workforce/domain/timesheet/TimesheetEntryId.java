package tech.kayys.numou.workforce.domain.timesheet;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record TimesheetEntryId(UUID value) implements DomainId<UUID> {
    public static TimesheetEntryId of(UUID value) { return new TimesheetEntryId(value); }
    public static TimesheetEntryId generate() { return new TimesheetEntryId(UUID.randomUUID()); }
}
