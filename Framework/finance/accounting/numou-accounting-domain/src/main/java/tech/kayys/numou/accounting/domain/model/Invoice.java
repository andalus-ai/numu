package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.identifier.CustomerId;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;
import tech.kayys.syirkah.accounting.domain.valueobject.PaymentMethod;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Invoice extends AbstractAggregateRoot<InvoiceId> {

    public record InvoiceLine(
            String productId,
            String description,
            int quantity,
            Money unitPrice,
            Money lineTotal,
            Money taxAmount,
            Money discountAmount
    ) implements ValueObject {
        public String getProductId() { return productId; }
        public String getDescription() { return description; }
        public int getQuantity() { return quantity; }
        public Money getUnitPrice() { return unitPrice; }
        public Money getLineTotal() { return lineTotal; }
        public Money getTaxAmount() { return taxAmount; }
        public Money getDiscountAmount() { return discountAmount; }
    }

    public record Payment(
            String transactionId,
            Money amount,
            PaymentMethod method,
            String reference,
            Instant date
    ) implements ValueObject {
        public String getTransactionId() { return transactionId; }
        public Money getAmount() { return amount; }
        public PaymentMethod getMethod() { return method; }
        public String getReference() { return reference; }
        public Instant getDate() { return date; }
    }

    private final InvoiceId id;
    private CustomerId customerId;
    private String invoiceNumber;
    private Instant invoiceDate;
    private Instant dueDate;
    private InvoiceStatus status;
    private Currency currency;
    private Money subtotal;
    private Money taxTotal;
    private Money discountTotal;
    private Money total;
    private Money paidAmount;
    private String customerNotes;
    private String purchaseOrderNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private final List<InvoiceLine> lines = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();

    public Invoice(InvoiceId id, CustomerId customerId, String invoiceNumber, Instant dueDate, Currency currency) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.customerId = Objects.requireNonNull(customerId);
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber);
        this.invoiceDate = Instant.now();
        this.dueDate = Objects.requireNonNull(dueDate);
        this.currency = Objects.requireNonNull(currency);
        this.status = InvoiceStatus.DRAFT;
        this.subtotal = Money.zero(currency);
        this.taxTotal = Money.zero(currency);
        this.discountTotal = Money.zero(currency);
        this.total = Money.zero(currency);
        this.paidAmount = Money.zero(currency);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @Override
    public InvoiceId id() { return id; }
    public InvoiceId getId() { return id; }

    public void addLine(String description, int quantity, Money unitPrice) {
        Money lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        Money zero = Money.zero(unitPrice.getCurrency());
        lines.add(new InvoiceLine(null, description, quantity, unitPrice, lineTotal, zero, zero));
        recalculateTotals();
    }

    public void addLine(String productId, String description, int quantity, Money unitPrice, Money taxAmount, Money discountAmount) {
        Money lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).add(taxAmount).subtract(discountAmount);
        lines.add(new InvoiceLine(productId, description, quantity, unitPrice, lineTotal, taxAmount, discountAmount));
        recalculateTotals();
    }

    private void recalculateTotals() {
        Currency cur = this.currency != null ? this.currency : (lines.isEmpty() ? Currency.of("USD") : lines.get(0).unitPrice().getCurrency());
        Money sub = Money.zero(cur);
        Money tax = Money.zero(cur);
        Money disc = Money.zero(cur);
        Money tot = Money.zero(cur);
        for (InvoiceLine line : lines) {
            sub = sub.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            tax = tax.add(line.taxAmount());
            disc = disc.add(line.discountAmount());
            tot = tot.add(line.lineTotal());
        }
        this.subtotal = sub;
        this.taxTotal = tax;
        this.discountTotal = disc;
        this.total = tot;
        this.updatedAt = Instant.now();
    }

    public void markSent() {
        if (status.canTransitionTo(InvoiceStatus.SENT)) {
            this.status = InvoiceStatus.SENT;
            this.updatedAt = Instant.now();
        }
    }

    public void recordPayment(String transactionId, Money amount, PaymentMethod method, String reference) {
        this.payments.add(new Payment(transactionId, amount, method, reference, Instant.now()));
        this.paidAmount = this.paidAmount.add(amount);
        if (this.paidAmount.amount().compareTo(this.total.amount()) >= 0) {
            this.status = InvoiceStatus.PAID;
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
        this.updatedAt = Instant.now();
    }

    public void recordPayment(Money amount) {
        recordPayment(UUID.randomUUID().toString(), amount, PaymentMethod.BANK_TRANSFER, null);
    }

    public CustomerId getCustomerId() { return customerId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Instant getInvoiceDate() { return invoiceDate; }
    public Instant getDueDate() { return dueDate; }
    public InvoiceStatus getStatus() { return status; }
    public Currency getCurrency() { return currency; }
    public Money getSubtotal() { return subtotal; }
    public Money getTaxTotal() { return taxTotal; }
    public Money getDiscountTotal() { return discountTotal; }
    public Money getTotal() { return total; }
    public Money getPaidAmount() { return paidAmount; }
    public Money getBalance() { return total.subtract(paidAmount); }
    public Money getRemainingBalance() { return getBalance(); }
    public String getCustomerNotes() { return customerNotes; }
    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }
    public String getPurchaseOrderNumber() { return purchaseOrderNumber; }
    public void setPurchaseOrderNumber(String purchaseOrderNumber) { this.purchaseOrderNumber = purchaseOrderNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }
}
