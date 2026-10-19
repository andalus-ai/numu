package tech.kayys.numou.workforce.domain.position;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a Position aggregate.
 */
public record PositionId(UUID value) implements DomainId<UUID> {

    public PositionId {
        Objects.requireNonNull(value, "Position ID cannot be null");
    }

    public static PositionId generate() {
        return new PositionId(UUID.randomUUID());
    }

    public static PositionId of(UUID value) {
        return new PositionId(value);
    }
}
