package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record WarrantyClaimId(UUID value) implements DomainId<UUID>, Serializable {
        public WarrantyClaimId {
        Objects.requireNonNull(value, "WarrantyClaimId value cannot be null");
    }
    public static WarrantyClaimId of(UUID value) { return new WarrantyClaimId(value); }
    public static WarrantyClaimId generate() { return new WarrantyClaimId(UUID.randomUUID()); }
    public static WarrantyClaimId fromString(String value) { return new WarrantyClaimId(UUID.fromString(value)); }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
