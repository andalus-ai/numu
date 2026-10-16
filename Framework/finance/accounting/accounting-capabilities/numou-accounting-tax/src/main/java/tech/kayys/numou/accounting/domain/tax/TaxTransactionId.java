package tech.kayys.syirkah.accounting.domain.tax;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a recorded tax transaction. */
public record TaxTransactionId(String value) {
    public TaxTransactionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("TaxTransactionId must not be blank");
    }
    public static TaxTransactionId generate() { return new TaxTransactionId(UUID.randomUUID().toString()); }
    public static TaxTransactionId of(String v) { return new TaxTransactionId(v); }
}
