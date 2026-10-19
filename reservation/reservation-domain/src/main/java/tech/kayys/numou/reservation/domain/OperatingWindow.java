package tech.kayys.numou.reservation.domain;

import java.time.LocalTime;
import java.util.Objects;

/**
 * An operating or service window during which reservations can begin.
 */
public record OperatingWindow(String name, LocalTime opens, LocalTime closes, LocalTime lastBooking) {
    public OperatingWindow {
        name = Objects.requireNonNull(name, "name cannot be null").trim();
        Objects.requireNonNull(opens, "opens cannot be null");
        Objects.requireNonNull(closes, "closes cannot be null");
        lastBooking = lastBooking == null ? closes : lastBooking;
    }

    public OperatingWindow(String name, LocalTime opens, LocalTime closes) {
        this(name, opens, closes, closes);
    }

    // Convenience compatibility accessor for restaurant contexts
    public LocalTime lastSeating() {
        return lastBooking;
    }
}
