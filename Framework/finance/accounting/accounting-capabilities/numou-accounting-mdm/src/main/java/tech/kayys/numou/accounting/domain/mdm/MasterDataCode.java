package tech.kayys.syirkah.accounting.domain.mdm;

import java.util.Objects;

/** Human-readable master data code (e.g. CUST-001, VEND-100). */
public record MasterDataCode(String value) {
    public MasterDataCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("MasterDataCode must not be blank");
    }
    public static MasterDataCode of(String v) { return new MasterDataCode(v); }
    @Override public String toString() { return value; }
}
