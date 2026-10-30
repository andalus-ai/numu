package tech.kayys.numou.ecosystem.domain.event;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a canonical person or organization identity is registered. */
public record PartyRegistered(
        UUID eventId,
        Instant occurredAt,
        UUID partyId,
        PartyKind kind
) implements DomainEvent {

    public PartyRegistered {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        if (kind == PartyKind.UNKNOWN) {
            throw new IllegalArgumentException("A registered Party cannot have kind UNKNOWN");
        }
    }

    public static PartyRegistered of(PartyId partyId, PartyKind kind) {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        return new PartyRegistered(UUID.randomUUID(), Instant.now(), partyId.value(), kind);
    }

    @Override
    public String eventType() {
        return "ecosystem.party.registered";
    }
}
