package tech.kayys.numou.restaurant.kernel.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Half-open interval {@code [start, end)}: back-to-back ranges do not overlap. */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public static TimeRange of(Instant start, Duration length) {
        return new TimeRange(start, start.plus(length));
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public Duration length() {
        return Duration.between(start, end);
    }
}
