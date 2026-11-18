package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.events.domain.HistoricalEventId;

import java.util.Objects;

/** Links an event to a historical place using a controlled, extensible role code. */
public record HistoricalEventPlace(
        HistoricalEventId eventId,
        HistoricalPlaceId placeId,
        String roleCode) {
    public HistoricalEventPlace {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(placeId, "Place ID is required");
        Objects.requireNonNull(roleCode, "Place role is required");
        roleCode = roleCode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!roleCode.matches("[a-z0-9][a-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid event-place role code");
    }
}
