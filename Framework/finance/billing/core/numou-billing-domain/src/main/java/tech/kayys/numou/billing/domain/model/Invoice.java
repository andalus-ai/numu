package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.billing.domain.identifier.InvoiceId;
import tech.kayys.numou.billing.domain.valueobject.BillingContext;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Universal Invoice Aggregate Root.
 * Generated from billing cycles, POS carts, FnB tables, or standalone orders.
 */
public final class Invoice extends AbstractAggregateRoot<InvoiceId> {

    private static final long serialVersionUID = 1L;

    public enum InvoiceStatus {
        DRAFT, PENDING, ISSUED, PAID, PARTIALLY_PAID, OVERDUE, VOID, CANCELLED
    }

    private String invoiceNumber;
    private BillingContext context;
    private String scheduleId;
    private InvoiceStatus status;
    private Instant invoiceDate;
    private Instant dueDate;
    private Money subtotal;
    private Money taxTotal;
    private Money discountTotal;
    private Money totalAmount;
    private Money paidAmount;
    private Money balanceDue;
    private List<BillingItem> lines;
    private String notes;

    private Invoice(InvoiceId id) {
        super(id);
        this.lines = new ArrayList<>();
        this.status = InvoiceStatus.DRAFT;
        this.invoiceDate = Instant.now();
        this.dueDate = invoiceDate.plus(30, ChronoUnit.DAYS);
    }

    public static Invoice create(InvoiceId id, String invoiceNumber, BillingContext context) {
        Invoice invoice = new Invoice(id);
        invoice.invoiceNumber = invoiceNumber;
        invoice.context = context;
        String cur = context.getCurrencyCode();
        invoice.subtotal = Money.zero(cur);
        invoice.taxTotal = Money.zero(cur);
        invoice.discountTotal = Money.zero(cur);
        invoice.totalAmount = Money.zero(cur);
        invoice.paidAmount = Money.zero(cur);
        invoice.balanceDue = Money.zero(cur);
        return invoice;
    }

    public void addLine(BillingItem item) {
        this.lines.add(item);
        recalculateTotals();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void addLines(List<BillingItem> items) {
        this.lines.addAll(items);
        recalculateTotals();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void recalculateTotals() {
        String cur = context != null ? context.getCurrencyCode() : "USD";
        Money sub = Money.zero(cur);
        Money tax = Money.zero(cur);
        Money disc = Money.zero(cur);
        for (BillingItem item : lines) {
            sub = sub.add(item.getUnitPrice().multiply(item.getQuantity()));
            tax = tax.add(item.getTaxAmount());
            disc = disc.add(item.getDiscountAmount());
        }
        this.subtotal = sub;
        this.taxTotal = tax;
        this.discountTotal = disc;
        this.totalAmount = sub.add(tax).subtract(disc);
        this.balanceDue = totalAmount.subtract(paidAmount != null ? paidAmount : Money.zero(cur));
    }

    public void issue() {
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.PENDING) {
            throw new IllegalStateException("Cannot issue invoice in status: " + status);
        }
        this.status = InvoiceStatus.ISSUED;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void recordPayment(Money payment) {
        if (payment == null || !payment.isPositive()) {
            throw new IllegalArgumentException("Payment must be positive");
        }
        this.paidAmount = this.paidAmount.add(payment);
        this.balanceDue = this.totalAmount.subtract(this.paidAmount);
        if (this.balanceDue.isZero() || this.balanceDue.isNegative()) {
            this.status = InvoiceStatus.PAID;
            this.balanceDue = Money.zero(totalAmount.getCurrency().getCurrencyCode());
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void cancel(String reason) {
        this.status = InvoiceStatus.CANCELLED;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "Cancelled: " + reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public String getInvoiceNumber() { return invoiceNumber; }
    public BillingContext getContext() { return context; }
    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getInvoiceDate() { return invoiceDate; }
    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
    public Money getSubtotal() { return subtotal; }
    public Money getTaxTotal() { return taxTotal; }
    public Money getDiscountTotal() { return discountTotal; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getPaidAmount() { return paidAmount; }
    public Money getBalanceDue() { return balanceDue; }
    public List<BillingItem> getLines() { return Collections.unmodifiableList(lines); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
