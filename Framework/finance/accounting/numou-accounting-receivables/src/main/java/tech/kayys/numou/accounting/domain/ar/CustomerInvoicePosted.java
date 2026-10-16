package tech.kayys.syirkah.accounting.domain.ar;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Raised when a CustomerInvoice is accepted into the AR subledger.
 * Posting rule: DR Accounts Receivable / CR Revenue (+ CR Tax).
 */
public record CustomerInvoicePosted(
        CustomerInvoiceId invoiceId,
        CustomerId customerId,
        String customerRef,
        String arAccount,
        String taxAccount,          // nullable
        List<CustomerInvoiceLine> lines,
        String currency,
        Instant occurredAt
) {
    public CustomerInvoicePosted {
        Objects.requireNonNull(invoiceId); Objects.requireNonNull(customerId);
        Objects.requireNonNull(customerRef); Objects.requireNonNull(arAccount);
        Objects.requireNonNull(lines);      Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
        lines = List.copyOf(lines);
    }
}
