package tech.kayys.numou.crm.domain.event.territory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a territory is renamed.
 */
public record TerritoryRenamed(
        UUID eventId,
        Instant occurredAt,
        UUID territoryId,
        String newName,
        long timestampMillis) implements DomainEvent {

    public TerritoryRenamed {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
    }

    public static TerritoryRenamed of(UUID territoryId,
                                      String newName,
                                      long timestampMillis) {
        return new TerritoryRenamed(
                UUID.randomUUID(),
                Instant.now(),
                territoryId,
                newName,
                timestampMillis
        );
    }

    @Override
    public String eventType() {
        return "crm.territory.renamed";
    }
}