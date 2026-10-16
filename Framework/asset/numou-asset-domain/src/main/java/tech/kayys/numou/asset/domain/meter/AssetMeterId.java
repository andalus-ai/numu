package tech.kayys.numou.asset.domain.meter;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Meter definition identity (ASSET-21 §21.5). */
public record AssetMeterId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetMeterId {
        Objects.requireNonNull(value, "AssetMeterId value cannot be null");
    }

    public static AssetMeterId of(UUID value) {
        return new AssetMeterId(value);
    }

    public static AssetMeterId generate() {
        return new AssetMeterId(UUID.randomUUID());
    }

    public static AssetMeterId fromString(String value) {
        return new AssetMeterId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AssetMeterId{" + value + "}";
    }
}
