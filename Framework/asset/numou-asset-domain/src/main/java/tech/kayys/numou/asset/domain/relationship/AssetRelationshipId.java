package tech.kayys.syirkah.asset.domain.relationship;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetRelationship}. */
public record AssetRelationshipId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetRelationshipId {
        Objects.requireNonNull(value, "AssetRelationshipId value cannot be null");
    }

    public static AssetRelationshipId of(UUID value) {
        return new AssetRelationshipId(value);
    }

    public static AssetRelationshipId generate() {
        return new AssetRelationshipId(UUID.randomUUID());
    }

    public static AssetRelationshipId fromString(String value) {
        return new AssetRelationshipId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
