package tech.kayys.syirkah.asset.domain.installation;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetInstallation}. */
public record AssetInstallationId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetInstallationId {
        Objects.requireNonNull(value, "AssetInstallationId value cannot be null");
    }

    public static AssetInstallationId of(UUID value) {
        return new AssetInstallationId(value);
    }

    public static AssetInstallationId generate() {
        return new AssetInstallationId(UUID.randomUUID());
    }

    public static AssetInstallationId fromString(String value) {
        return new AssetInstallationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
