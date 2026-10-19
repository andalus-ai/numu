package tech.kayys.numou.scope.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a node within a breakdown structure. */
public record BreakdownNodeId(UUID value) implements DomainId<UUID> {
    public BreakdownNodeId {
        Objects.requireNonNull(value, "Breakdown node id cannot be null");
    }

    public static BreakdownNodeId generate() {
        return new BreakdownNodeId(UUID.randomUUID());
    }
}
