package tech.kayys.syirkah.accounting.domain.tax;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a periodic tax return. */
public record TaxReturnId(String value) {
    public TaxReturnId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("TaxReturnId must not be blank");
    }
    public static TaxReturnId generate() { return new TaxReturnId(UUID.randomUUID().toString()); }
    public static TaxReturnId of(String v) { return new TaxReturnId(v); }
}
