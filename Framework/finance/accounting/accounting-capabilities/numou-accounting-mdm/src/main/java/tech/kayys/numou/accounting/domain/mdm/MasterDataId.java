package tech.kayys.syirkah.accounting.domain.mdm;

import java.util.Objects;
import java.util.UUID;

/** Stable immutable identity of a master data record. */
public record MasterDataId(String value) {
    public MasterDataId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("MasterDataId must not be blank");
    }
    public static MasterDataId generate() { return new MasterDataId(UUID.randomUUID().toString()); }
    public static MasterDataId of(String v) { return new MasterDataId(v); }
}
