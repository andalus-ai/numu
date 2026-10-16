package tech.kayys.syirkah.asset.domain.document;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetDocumentReference}. */
public record AssetDocumentReferenceId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetDocumentReferenceId {
        Objects.requireNonNull(value, "AssetDocumentReferenceId value cannot be null");
    }

    public static AssetDocumentReferenceId of(UUID value) {
        return new AssetDocumentReferenceId(value);
    }

    public static AssetDocumentReferenceId generate() {
        return new AssetDocumentReferenceId(UUID.randomUUID());
    }

    public static AssetDocumentReferenceId fromString(String value) {
        return new AssetDocumentReferenceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
