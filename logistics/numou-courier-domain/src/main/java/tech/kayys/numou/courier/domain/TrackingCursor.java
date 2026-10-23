package tech.kayys.numou.courier.domain;

import java.time.Instant;
import java.util.Objects;

/** Stable exclusive timeline cursor; both fields are required to page events sharing a timestamp. */
public record TrackingCursor(Instant occurredAt, TrackingEventId eventId) {
    public TrackingCursor {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
    }

    public static TrackingCursor after(TrackingEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return new TrackingCursor(event.occurredAt(), event.id());
    }
}
