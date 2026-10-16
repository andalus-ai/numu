package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory's parent is changed.
 */
public record TerritoryParentChanged(
        UUID eventId,
        Instant occurredAt,
        UUID territoryId,
        UUID oldParentTerritoryId,
        UUID newParentTerritoryId,
        long timestampMillis) implements DomainEvent {

    public TerritoryParentChanged {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
    }

    public static TerritoryParentChanged of(UUID territoryId,
                                            UUID oldParentTerritoryId,
                                            UUID newParentTerritoryId,
                                            long timestampMillis) {
        return new TerritoryParentChanged(
                UUID.randomUUID(),
                Instant.now(),
                territoryId,
                oldParentTerritoryId,
                newParentTerritoryId,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.parent-changed";
    }
}