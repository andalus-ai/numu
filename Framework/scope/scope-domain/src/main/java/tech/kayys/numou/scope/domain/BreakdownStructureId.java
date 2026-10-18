package tech.kayys.numou.scope.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a reusable breakdown structure. */
public record BreakdownStructureId(UUID value) implements DomainId<UUID> {
    public BreakdownStructureId {
        Objects.requireNonNull(value, "Breakdown structure id cannot be null");
    }

    public static BreakdownStructureId generate() {
        return new BreakdownStructureId(UUID.randomUUID());
    }
}
