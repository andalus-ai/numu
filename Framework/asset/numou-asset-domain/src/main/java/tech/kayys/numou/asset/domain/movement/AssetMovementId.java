package tech.kayys.syirkah.asset.domain.movement;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetMovement} history entry. */
public record AssetMovementId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetMovementId {
        Objects.requireNonNull(value, "AssetMovementId value cannot be null");
    }

    public static AssetMovementId of(UUID value) {
        return new AssetMovementId(value);
    }

    public static AssetMovementId generate() {
        return new AssetMovementId(UUID.randomUUID());
    }

    public static AssetMovementId fromString(String value) {
        return new AssetMovementId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
