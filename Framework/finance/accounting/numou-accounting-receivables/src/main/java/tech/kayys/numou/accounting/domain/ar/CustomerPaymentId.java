package tech.kayys.syirkah.accounting.domain.ar;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link CustomerPayment} aggregate. */
public record CustomerPaymentId(String value) {
    public CustomerPaymentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("CustomerPaymentId must not be blank");
    }
    public static CustomerPaymentId generate() { return new CustomerPaymentId(UUID.randomUUID().toString()); }
    public static CustomerPaymentId of(String v) { return new CustomerPaymentId(v); }
}
