package tech.kayys.syirkah.accounting.domain.ap;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link VendorInvoice} aggregate. */
public record VendorInvoiceId(String value) {
    public VendorInvoiceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("VendorInvoiceId must not be blank");
    }
    public static VendorInvoiceId generate() { return new VendorInvoiceId(UUID.randomUUID().toString()); }
    public static VendorInvoiceId of(String v) { return new VendorInvoiceId(v); }
}
