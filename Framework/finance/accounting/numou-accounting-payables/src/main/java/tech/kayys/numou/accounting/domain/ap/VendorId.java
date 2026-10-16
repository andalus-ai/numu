package tech.kayys.syirkah.accounting.domain.ap;

import java.util.Objects;

/** Strongly-typed vendor identity owned by the Master-Data bounded context. */
public record VendorId(String value) {
    public VendorId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("VendorId must not be blank");
    }
    public static VendorId of(String v) { return new VendorId(v); }
}
