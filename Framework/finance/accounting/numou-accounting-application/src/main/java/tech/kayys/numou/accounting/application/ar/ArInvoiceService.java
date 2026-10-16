package tech.kayys.syirkah.accounting.application.ar;

import tech.kayys.syirkah.accounting.domain.ar.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service orchestrating Accounts Receivable invoice and payment lifecycle.
 */
public final class ArInvoiceService {

    private final CustomerInvoiceRepository repository;

    public ArInvoiceService(CustomerInvoiceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /** Registers a new customer invoice in DRAFT status. */
    public CustomerInvoice register(CustomerInvoiceId id, CustomerId customerId,
                                     String customerRef, String currency,
                                     List<CustomerInvoiceLine> lines) {
        CustomerInvoice invoice = new CustomerInvoice(id, customerId, customerRef, currency, lines);
        repository.save(invoice);
        return invoice;
    }

    /** Posts a DRAFT invoice into the AR subledger (DRAFT → POSTED). */
    public CustomerInvoice post(CustomerInvoiceId id, String arAccount, String taxAccount) {
        CustomerInvoice inv = load(id);
        inv.post(arAccount, taxAccount);
        repository.save(inv);
        return inv;
    }

    /** Applies a cash payment to a POSTED invoice (may transition to PAID). */
    public CustomerInvoice receivePayment(CustomerInvoiceId invoiceId,
                                           CustomerPaymentId paymentId,
                                           BigDecimal amount,
                                           String cashAccount,
                                           String arAccount) {
        CustomerInvoice inv = load(invoiceId);
        inv.receivePayment(paymentId, amount, cashAccount, arAccount);
        repository.save(inv);
        return inv;
    }

    /** Cancels a DRAFT invoice. */
    public CustomerInvoice cancel(CustomerInvoiceId id) {
        CustomerInvoice inv = load(id);
        inv.cancel();
        repository.save(inv);
        return inv;
    }

    public Optional<CustomerInvoice> find(CustomerInvoiceId id) { return repository.find(id); }

    private CustomerInvoice load(CustomerInvoiceId id) {
        return repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("CustomerInvoice not found: " + id.value()));
    }
}
