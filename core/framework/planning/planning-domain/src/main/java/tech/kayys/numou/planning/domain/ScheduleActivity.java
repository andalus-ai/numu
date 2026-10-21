package tech.kayys.numou.planning.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Immutable planned activity; date changes replace the value in the editable plan. */
public record ScheduleActivity(
        ActivityId id,
        String code,
        String name,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        WorkItemReference workItem,
        int position) {

    public ScheduleActivity {
        Objects.requireNonNull(id, "Activity id cannot be null");
        code = requireText(code, "Activity code");
        name = requireText(name, "Activity name");
        Objects.requireNonNull(plannedStart, "Planned start cannot be null");
        Objects.requireNonNull(plannedFinish, "Planned finish cannot be null");
        if (plannedFinish.isBefore(plannedStart)) {
            throw new IllegalArgumentException("Planned finish cannot be before planned start");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Activity position cannot be negative");
        }
    }

    public static ScheduleActivity create(
            String code, String name, LocalDate start, LocalDate finish,
            WorkItemReference workItem, int position) {
        return new ScheduleActivity(ActivityId.generate(), code, name, start, finish, workItem, position);
    }

    public Optional<WorkItemReference> linkedWorkItem() {
        return Optional.ofNullable(workItem);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
