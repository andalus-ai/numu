package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory is created.
 */
public record TerritoryCreated(
        UUID eventId,
        Instant occurredAt,
        UUID territoryId,
        String name,
        String description,
        UUID parentTerritoryId,
        long timestampMillis) implements DomainEvent {

    public TerritoryCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
    }

    public static TerritoryCreated of(UUID territoryId,
                                      String name,
                                      String description,
                                      UUID parentTerritoryId,
                                      long timestampMillis) {
        return new TerritoryCreated(
                UUID.randomUUID(),
                Instant.now(),
                territoryId,
                name,
                description,
                parentTerritoryId,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.created";
    }
}