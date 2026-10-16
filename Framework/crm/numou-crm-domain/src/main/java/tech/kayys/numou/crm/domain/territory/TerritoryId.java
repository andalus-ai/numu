package tech.kayys.numou.crm.domain.territory;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for a {@link Territory}.
 */
public record TerritoryId(UUID value) implements DomainId<UUID>, Serializable {
        public TerritoryId {
        value = Objects.requireNonNull(value, "value cannot be null");
    }

    public static TerritoryId generate() {
        return new TerritoryId(UUID.randomUUID());
    }

    public static TerritoryId of(UUID value) {
        return new TerritoryId(value);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}