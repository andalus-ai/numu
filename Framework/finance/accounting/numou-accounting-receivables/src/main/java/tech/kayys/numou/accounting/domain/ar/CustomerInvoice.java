package tech.kayys.syirkah.accounting.domain.ar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Root aggregate for a customer invoice in Accounts Receivable.
 *
 * <pre>DRAFT --post()--> POSTED --pay()--> PAID  |  DRAFT --cancel()--> CANCELLED</pre>
 */
public final class CustomerInvoice {

    private final CustomerInvoiceId id;
    private final CustomerId customerId;
    private final String customerRef;
    private final String currency;
    private final List<CustomerInvoiceLine> lines;
    private CustomerInvoiceStatus status;
    private BigDecimal outstandingBalance;

    private final List<Object> domainEvents = new ArrayList<>();

    public CustomerInvoice(CustomerInvoiceId id, CustomerId customerId, String customerRef,
                            String currency, List<CustomerInvoiceLine> lines) {
        this.id          = Objects.requireNonNull(id);
        this.customerId  = Objects.requireNonNull(customerId);
        this.customerRef = requireText(customerRef, "customerRef");
        this.currency    = requireText(currency, "currency");
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("CustomerInvoice must have at least one line");
        this.lines              = List.copyOf(lines);
        this.status             = CustomerInvoiceStatus.DRAFT;
        this.outstandingBalance = totalAmount();
        domainEvents.add(new CustomerInvoiceRegistered(id, customerId, customerRef, currency, Instant.now()));
    }

    /** DRAFT → POSTED, raises {@link CustomerInvoicePosted}. */
    public void post(String arAccount, String taxAccount) {
        if (status != CustomerInvoiceStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT invoices can be posted; current=" + status);
        requireText(arAccount, "arAccount");
        this.status = CustomerInvoiceStatus.POSTED;
        domainEvents.add(new CustomerInvoicePosted(
                id, customerId, customerRef, arAccount, taxAccount, lines, currency, Instant.now()));
    }

    /** Applies a payment amount and raises {@link CustomerPaymentReceived}. */
    public void receivePayment(CustomerPaymentId paymentId, BigDecimal amount,
                                String cashAccount, String arAccount) {
        if (status != CustomerInvoiceStatus.POSTED)
            throw new IllegalStateException("Payment can only be applied to POSTED invoices; current=" + status);
        Objects.requireNonNull(paymentId); requireText(cashAccount, "cashAccount"); requireText(arAccount, "arAccount");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        if (amount.compareTo(outstandingBalance) > 0)
            throw new IllegalArgumentException("Payment exceeds outstanding balance " + outstandingBalance);
        outstandingBalance = outstandingBalance.subtract(amount);
        if (outstandingBalance.signum() == 0) this.status = CustomerInvoiceStatus.PAID;
        domainEvents.add(new CustomerPaymentReceived(
                paymentId, id, customerId, amount, currency, cashAccount, arAccount, Instant.now()));
    }

    /** DRAFT → CANCELLED. */
    public void cancel() {
        if (status != CustomerInvoiceStatus.DRAFT)
            throw new IllegalStateException("Only DRAFT invoices can be cancelled; current=" + status);
        this.status = CustomerInvoiceStatus.CANCELLED;
    }

    public BigDecimal totalAmount() {
        return lines.stream().map(CustomerInvoiceLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal netAmount() {
        return lines.stream().map(CustomerInvoiceLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal taxAmount() {
        return lines.stream().map(CustomerInvoiceLine::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public CustomerInvoiceId id()          { return id; }
    public CustomerId customerId()         { return customerId; }
    public String customerRef()            { return customerRef; }
    public String currency()               { return currency; }
    public List<CustomerInvoiceLine> lines(){ return lines; }
    public CustomerInvoiceStatus status()  { return status; }
    public BigDecimal outstandingBalance() { return outstandingBalance; }
    public List<Object> domainEvents()     { return List.copyOf(domainEvents); }
    public void clearEvents()              { domainEvents.clear(); }

    /**
     * Restores persisted state without emitting a new domain event.
     * This is intended for repository adapters only.
     */
    public void restorePersistedState(CustomerInvoiceStatus persistedStatus, BigDecimal persistedOutstanding) {
        Objects.requireNonNull(persistedStatus);
        Objects.requireNonNull(persistedOutstanding);
        if (persistedOutstanding.signum() < 0 || persistedOutstanding.compareTo(totalAmount()) > 0) {
            throw new IllegalArgumentException("invalid persisted outstanding balance");
        }
        this.status = persistedStatus;
        this.outstandingBalance = persistedOutstanding;
        clearEvents();
    }

    private static String requireText(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return v;
    }
}
