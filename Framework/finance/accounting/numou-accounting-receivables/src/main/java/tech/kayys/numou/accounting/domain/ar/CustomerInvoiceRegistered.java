package tech.kayys.syirkah.accounting.domain.ar;

import java.time.Instant;
import java.util.Objects;

/** Raised when a CustomerInvoice is first captured in DRAFT status. */
public record CustomerInvoiceRegistered(
        CustomerInvoiceId invoiceId,
        CustomerId customerId,
        String customerRef,
        String currency,
        Instant occurredAt
) {
    public CustomerInvoiceRegistered {
        Objects.requireNonNull(invoiceId); Objects.requireNonNull(customerId);
        Objects.requireNonNull(customerRef); Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
    }
}
