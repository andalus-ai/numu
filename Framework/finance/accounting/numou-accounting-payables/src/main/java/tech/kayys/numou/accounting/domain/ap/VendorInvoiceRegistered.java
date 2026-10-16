package tech.kayys.syirkah.accounting.domain.ap;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a VendorInvoice is first captured in DRAFT status. */
public record VendorInvoiceRegistered(
        VendorInvoiceId invoiceId,
        VendorId vendorId,
        String vendorRef,
        String currency,
        Instant occurredAt
) {
    public VendorInvoiceRegistered {
        Objects.requireNonNull(invoiceId); Objects.requireNonNull(vendorId);
        Objects.requireNonNull(vendorRef); Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
    }
}
