package tech.kayys.numou.crm.domain.territory;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for a {@link TerritoryAssignment}.
 */
public record TerritoryAssignmentId(UUID value) implements DomainId<UUID>, Serializable {
        public TerritoryAssignmentId {
        value = Objects.requireNonNull(value, "value cannot be null");
    }

    public static TerritoryAssignmentId generate() {
        return new TerritoryAssignmentId(UUID.randomUUID());
    }

    public static TerritoryAssignmentId of(UUID value) {
        return new TerritoryAssignmentId(value);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}