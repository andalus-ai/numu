package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory is activated.
 */
public record TerritoryActivated(
        UUID eventId,
        Instant occurredAt,
        UUID territoryId,
        long timestampMillis) implements DomainEvent {

    public TerritoryActivated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
    }

    public static TerritoryActivated of(UUID territoryId,
                                        long timestampMillis) {
        return new TerritoryActivated(
                UUID.randomUUID(),
                Instant.now(),
                territoryId,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.activated";
    }
}