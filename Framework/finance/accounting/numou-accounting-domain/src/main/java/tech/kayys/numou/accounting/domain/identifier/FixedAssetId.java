package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FixedAssetId(UUID value) implements DomainId<UUID> {
    public FixedAssetId {
        Objects.requireNonNull(value, "FixedAssetId value cannot be null");
    }
    public UUID getValue() { return value; }
    public static FixedAssetId generate() { return new FixedAssetId(UUID.randomUUID()); }
    public static FixedAssetId of(UUID value) { return new FixedAssetId(value); }
    public static FixedAssetId of(String value) { return new FixedAssetId(UUID.fromString(value)); }
}
