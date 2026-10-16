package tech.kayys.syirkah.accounting.receivables.application;

import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.repository.InvoiceRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InvoiceService {
    private final InvoiceRepository invoices;
    public InvoiceService(InvoiceRepository invoices) { this.invoices = Objects.requireNonNull(invoices); }
    public CustomerInvoice register(CustomerInvoiceId id, CustomerId customerId, String reference,
                                    String currency, List<CustomerInvoiceLine> lines) {
        if (invoices.find(id).isPresent()) throw new IllegalStateException("invoice already exists: " + id.value());
        return invoices.save(new CustomerInvoice(id, customerId, reference, currency, lines));
    }
    public CustomerInvoice post(CustomerInvoiceId id, String arAccount, String taxAccount) {
        var invoice = require(id); invoice.post(arAccount, taxAccount); return invoices.save(invoice);
    }
    public CustomerInvoice cancel(CustomerInvoiceId id) {
        var invoice = require(id); invoice.cancel(); return invoices.save(invoice);
    }
    public CustomerInvoice require(CustomerInvoiceId id) {
        return invoices.find(id).orElseThrow(() -> new java.util.NoSuchElementException("unknown invoice: " + id.value()));
    }
    public Optional<CustomerInvoice> find(CustomerInvoiceId id) {
        return invoices.find(id);
    }
}
