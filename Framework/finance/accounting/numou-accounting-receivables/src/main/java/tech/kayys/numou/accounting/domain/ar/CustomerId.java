package tech.kayys.syirkah.accounting.domain.ar;

import java.util.Objects;

/** Strongly-typed customer identity (owned by CRM / MDM bounded contexts). */
public record CustomerId(String value) {
    public CustomerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("CustomerId must not be blank");
    }
    public static CustomerId of(String v) { return new CustomerId(v); }
}
