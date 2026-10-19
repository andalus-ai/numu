package tech.kayys.numou.ecosystem.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a participant joins the Numou ecosystem.
 */
public record ParticipantRegistered(
        UUID eventId,
        Instant occurredAt,
        UUID participantId,
        String participantCode,
        String participantName,
        String participantType) implements DomainEvent {

    public ParticipantRegistered {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(participantCode, "participantCode cannot be null");
        Objects.requireNonNull(participantName, "participantName cannot be null");
        Objects.requireNonNull(participantType, "participantType cannot be null");
    }

    public static ParticipantRegistered of(
            UUID participantId,
            String participantCode,
            String participantName,
            String participantType) {
        return new ParticipantRegistered(
                UUID.randomUUID(),
                Instant.now(),
                participantId,
                participantCode,
                participantName,
                participantType
        );
    }

    @Override
    public String eventType() {
        return "ecosystem.participant.registered";
    }
}
