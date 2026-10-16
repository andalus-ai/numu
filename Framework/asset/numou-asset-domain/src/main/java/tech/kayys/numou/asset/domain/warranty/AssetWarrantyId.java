package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier for {@link AssetWarranty} (ASSET-23). */
public record AssetWarrantyId(UUID value) implements DomainId<UUID>, Serializable {
        public AssetWarrantyId {
        Objects.requireNonNull(value, "AssetWarrantyId value cannot be null");
    }
    public static AssetWarrantyId of(UUID value) { return new AssetWarrantyId(value); }
    public static AssetWarrantyId generate() { return new AssetWarrantyId(UUID.randomUUID()); }
    public static AssetWarrantyId fromString(String value) { return new AssetWarrantyId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
