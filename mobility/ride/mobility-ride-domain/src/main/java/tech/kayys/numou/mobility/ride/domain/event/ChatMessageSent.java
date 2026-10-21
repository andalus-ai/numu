package tech.kayys.numou.mobility.ride.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record ChatMessageSent(
        UUID eventId,
        Instant occurredAt,
        UUID rideId,
        String sender) implements DomainEvent {

    public static ChatMessageSent at(Instant now, UUID rideId, String sender) {
        return new ChatMessageSent(UUID.randomUUID(), now, rideId, sender);
    }

    @Override
    public String eventType() {
        return "mobility.ride.chat-message";
    }
}
