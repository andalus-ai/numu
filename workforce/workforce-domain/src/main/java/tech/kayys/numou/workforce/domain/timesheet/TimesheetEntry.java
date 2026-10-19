package tech.kayys.numou.workforce.domain.timesheet;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

public record TimesheetEntry(
        TimesheetEntryId id,
        LocalDate date,
        Duration duration,
        WorkContextRef context,
        String description
) {
    public TimesheetEntry {
        Objects.requireNonNull(id, "TimesheetEntryId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Entry duration must be positive");
        }
        Objects.requireNonNull(context, "context must not be null");
    }
}
