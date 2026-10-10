package tech.kayys.syirkah.reservation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TimeRange(Instant start, Instant end) {
    public TimeRange {
        Objects.requireNonNull(start, "start cannot be null");
        Objects.requireNonNull(end, "end cannot be null");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End must be after start");
        }
    }

    public static TimeRange of(Instant start, Instant end) {
        return new TimeRange(start, end);
    }

    public static TimeRange of(Instant start, Duration duration) {
        return new TimeRange(start, start.plus(duration));
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
