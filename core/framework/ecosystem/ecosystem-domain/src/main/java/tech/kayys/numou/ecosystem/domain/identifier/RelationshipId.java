package tech.kayys.numou.ecosystem.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies a participant relationship - the directed link that lets
 * Numou understand "PT XYZ buys from PT ABC" or "PT ABC serves
 * 200 merchants" without hard-coding either side.
 */
public record RelationshipId(UUID value) implements DomainId<UUID>, Serializable {

    public RelationshipId {
        Objects.requireNonNull(value, "RelationshipId value cannot be null");
    }

    public static RelationshipId of(UUID value) {
        return new RelationshipId(value);
    }

    public static RelationshipId generate() {
        return new RelationshipId(UUID.randomUUID());
    }

    public static RelationshipId fromString(String value) {
        return new RelationshipId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "RelationshipId{" + value + "}";
    }
}
