package tech.kayys.numou.scope.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Identity of one immutable snapshot of a breakdown structure. */
public record BreakdownRevisionId(UUID value) implements DomainId<UUID> {
    public BreakdownRevisionId {
        Objects.requireNonNull(value, "Breakdown revision id cannot be null");
    }

    public static BreakdownRevisionId generate() {
        return new BreakdownRevisionId(UUID.randomUUID());
    }
}
