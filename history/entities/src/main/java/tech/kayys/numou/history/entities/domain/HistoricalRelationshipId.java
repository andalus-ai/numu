package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one historical relationship assertion. */
public record HistoricalRelationshipId(UUID value) {
    public HistoricalRelationshipId {
        Objects.requireNonNull(value, "Relationship ID is required");
    }
    public static HistoricalRelationshipId generate() {
        return new HistoricalRelationshipId(UUID.randomUUID());
    }
}
