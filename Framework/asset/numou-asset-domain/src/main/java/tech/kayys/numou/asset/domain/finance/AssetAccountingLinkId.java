package tech.kayys.syirkah.asset.domain.finance;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetAccountingLink}. */
public record AssetAccountingLinkId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetAccountingLinkId {
        Objects.requireNonNull(value, "AssetAccountingLinkId value cannot be null");
    }

    public static AssetAccountingLinkId of(UUID value) {
        return new AssetAccountingLinkId(value);
    }

    public static AssetAccountingLinkId generate() {
        return new AssetAccountingLinkId(UUID.randomUUID());
    }

    public static AssetAccountingLinkId fromString(String value) {
        return new AssetAccountingLinkId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
