package tech.kayys.syirkah.accounting.domain.ar;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link CustomerInvoice} aggregate. */
public record CustomerInvoiceId(String value) {
    public CustomerInvoiceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("CustomerInvoiceId must not be blank");
    }
    public static CustomerInvoiceId generate() { return new CustomerInvoiceId(UUID.randomUUID().toString()); }
    public static CustomerInvoiceId of(String v) { return new CustomerInvoiceId(v); }
}
