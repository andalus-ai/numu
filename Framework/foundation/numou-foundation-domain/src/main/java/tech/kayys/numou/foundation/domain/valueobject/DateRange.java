package tech.kayys.numou.foundation.domain.valueobject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Inclusive date range.
 *
 * Reused across accounting periods, promotion periods, subscription
 * periods, price validity, contracts, academic terms, project dates.
 */
public record DateRange(
        LocalDate start,
        LocalDate end
) implements ValueObject {

    public DateRange {
        Objects.requireNonNull(
                start,
                "Start date cannot be null"
        );

        Objects.requireNonNull(
                end,
                "End date cannot be null"
        );

        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date);

        return !date.isBefore(start)
                && !date.isAfter(end);
    }

    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other);

        return !end.isBefore(other.start)
                && !other.end.isBefore(start);
    }

    public boolean isSingleDay() {
        return start.equals(end);
    }

}
