package tech.kayys.syirkah.accounting.domain.ap;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Raised when a VendorInvoice is accepted into the AP subledger.
 * Carries everything the posting rule needs — self-contained.
 */
public record SupplierInvoicePosted(
        VendorInvoiceId invoiceId,
        VendorId vendorId,
        String vendorRef,
        String apAccount,
        String taxAccount,          // nullable when no tax lines
        List<VendorInvoiceLine> lines,
        String currency,
        Instant occurredAt
) {
    public SupplierInvoicePosted {
        Objects.requireNonNull(invoiceId); Objects.requireNonNull(vendorId);
        Objects.requireNonNull(vendorRef); Objects.requireNonNull(apAccount);
        Objects.requireNonNull(lines);     Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
        lines = List.copyOf(lines);
    }
}
