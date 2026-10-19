package tech.kayys.numou.workforce.domain.position;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a PositionAssignment aggregate.
 */
public record PositionAssignmentId(UUID value) implements DomainId<UUID> {

    public PositionAssignmentId {
        Objects.requireNonNull(value, "PositionAssignment ID must not be null");
    }

    public static PositionAssignmentId generate() {
        return new PositionAssignmentId(UUID.randomUUID());
    }

    public static PositionAssignmentId of(UUID value) {
        return new PositionAssignmentId(value);
    }
}
