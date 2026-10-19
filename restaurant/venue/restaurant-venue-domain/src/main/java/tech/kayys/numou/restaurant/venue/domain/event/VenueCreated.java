package tech.kayys.numou.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record VenueCreated(
        UUID eventId,
        Instant occurredAt,
        UUID venueId,
        String name) implements DomainEvent {

    public static VenueCreated at(Instant now, UUID venueId, String name) {
        return new VenueCreated(UUID.randomUUID(), now, venueId, name);
    }

    @Override
    public String eventType() {
        return "restaurant.venue.created";
    }
}
