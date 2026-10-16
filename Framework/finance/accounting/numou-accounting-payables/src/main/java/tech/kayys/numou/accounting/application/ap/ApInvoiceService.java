package tech.kayys.syirkah.accounting.application.ap;

import tech.kayys.syirkah.accounting.domain.ap.*;

import java.util.List;
import java.util.Objects;

/**
 * Application service orchestrating Accounts Payable invoice lifecycle.
 * Depends only on the pure-domain types — no framework coupling.
 */
public final class ApInvoiceService {

    private final VendorInvoiceRepository repository;

    public ApInvoiceService(VendorInvoiceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /**
     * Registers a new vendor invoice in DRAFT status.
     *
     * @return the created aggregate
     */
    public VendorInvoice register(VendorInvoiceId id, VendorId vendorId,
                                   String vendorRef, String currency,
                                   List<VendorInvoiceLine> lines) {
        VendorInvoice invoice = new VendorInvoice(id, vendorId, vendorRef, currency, lines);
        repository.save(invoice);
        return invoice;
    }

    /**
     * Posts a DRAFT invoice into the AP subledger.
     *
     * @return the updated aggregate (events contain {@link SupplierInvoicePosted})
     */
    public VendorInvoice post(VendorInvoiceId id, String apAccount, String taxAccount) {
        VendorInvoice invoice = repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("VendorInvoice not found: " + id.value()));
        invoice.post(apAccount, taxAccount);
        repository.save(invoice);
        return invoice;
    }

    /**
     * Cancels a DRAFT invoice.
     *
     * @return the updated aggregate
     */
    public VendorInvoice cancel(VendorInvoiceId id) {
        VendorInvoice invoice = repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("VendorInvoice not found: " + id.value()));
        invoice.cancel();
        repository.save(invoice);
        return invoice;
    }

    public java.util.Optional<VendorInvoice> find(VendorInvoiceId id) { return repository.find(id); }
}
