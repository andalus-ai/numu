package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.billing.domain.identifier.CreditNoteId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Credit Note aggregate root.
 * Represents a credit issued to a customer.
 */
public final class CreditNote extends AbstractAggregateRoot<CreditNoteId> {
    
    private static final long serialVersionUID = 1L;
    
    private String creditNoteNumber;
    private String customerId;
    private String customerEmail;
    private List<CreditNoteLine> lines;
    private Money totalAmount;
    private String currencyCode;
    private String reason;
    private String originalInvoiceId;
    private String originalTransactionId;
    private CreditNoteStatus status;
    private Instant issuedDate;
    private Instant expiryDate;
    private Instant appliedDate;
    private String appliedToInvoiceId;
    private Money remainingBalance;
    private String issuedBy;
    private String approvedBy;
    private String notes;
    private boolean active;

    private CreditNote(CreditNoteId id) {
        super(id);
        this.lines = new ArrayList<>();
        this.status = CreditNoteStatus.PENDING;
        this.active = true;
        this.issuedDate = Instant.now();
        this.totalAmount = Money.zero("USD");
        this.remainingBalance = Money.zero("USD");
    }

    private CreditNote() {
        super();
    }

    /**
     * Factory method to create a new credit note.
     */
    public static CreditNote create(
            CreditNoteId id,
            String creditNoteNumber,
            String customerId,
            String currencyCode,
            String reason) {
        CreditNote creditNote = new CreditNote(id);
        creditNote.creditNoteNumber = creditNoteNumber;
        creditNote.customerId = customerId;
        creditNote.currencyCode = currencyCode;
        creditNote.reason = reason;
        return creditNote;
    }

    /**
     * Adds a line to the credit note.
     */
    public void addLine(CreditNoteLine line) {
        if (status != CreditNoteStatus.PENDING) {
            throw new IllegalStateException("Cannot modify credit note in status: " + status);
        }
        lines.add(line);
        recalculateTotal();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Issues the credit note.
     */
    public void issue() {
        if (status != CreditNoteStatus.PENDING) {
            throw new IllegalStateException("Cannot issue credit note in status: " + status);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Credit note must have at least one line");
        }
        this.status = CreditNoteStatus.ISSUED;
        this.issuedDate = Instant.now();
        this.remainingBalance = totalAmount;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Applies the credit note to an invoice.
     */
    public void applyToInvoice(String invoiceId, Money amount) {
        if (status != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("Cannot apply credit note in status: " + status);
        }
        if (amount.isGreaterThan(remainingBalance)) {
            throw new IllegalArgumentException("Amount exceeds remaining balance");
        }

        this.appliedToInvoiceId = invoiceId;
        this.appliedDate = Instant.now();
        this.remainingBalance = remainingBalance.subtract(amount);

        if (remainingBalance.isZero()) {
            this.status = CreditNoteStatus.APPLIED;
        }

        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Voids the credit note.
     */
    public void voidNote(String reason) {
        if (status == CreditNoteStatus.APPLIED || status == CreditNoteStatus.EXPIRED) {
            throw new IllegalStateException("Cannot void applied or expired credit note");
        }
        this.status = CreditNoteStatus.VOIDED;
        this.active = false;
        this.notes = reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Expires the credit note.
     */
    public void expire() {
        if (status == CreditNoteStatus.APPLIED) {
            return;
        }
        this.status = CreditNoteStatus.EXPIRED;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void recalculateTotal() {
        this.totalAmount = lines.stream()
            .map(CreditNoteLine::getAmount)
            .reduce(Money.zero(currencyCode), Money::add);
        this.remainingBalance = totalAmount;
    }

    /**
     * Gets the available balance on the credit note.
     */
    public Money getAvailableBalance() {
        if (status != CreditNoteStatus.ISSUED) {
            return Money.zero(currencyCode);
        }
        return remainingBalance;
    }

    // Getters
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public List<CreditNoteLine> getLines() { return Collections.unmodifiableList(lines); }
    public Money getTotalAmount() { return totalAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getReason() { return reason; }
    public String getOriginalInvoiceId() { return originalInvoiceId; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public CreditNoteStatus getStatus() { return status; }
    public Instant getIssuedDate() { return issuedDate; }
    public Instant getExpiryDate() { return expiryDate; }
    public Instant getAppliedDate() { return appliedDate; }
    public String getAppliedToInvoiceId() { return appliedToInvoiceId; }
    public Money getRemainingBalance() { return remainingBalance; }
    public String getIssuedBy() { return issuedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setOriginalInvoiceId(String originalInvoiceId) {
        this.originalInvoiceId = originalInvoiceId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setOriginalTransactionId(String originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setExpiryDate(Instant expiryDate) {
        if (expiryDate != null && expiryDate.isBefore(issuedDate)) {
            throw new IllegalArgumentException("Expiry date must be after issue date");
        }
        this.expiryDate = expiryDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "CreditNote{" +
                "id=" + getId() +
                ", creditNoteNumber='" + creditNoteNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", totalAmount=" + totalAmount +
                ", status=" + status +
                '}';
    }

    /**
     * Credit note status enum.
     */
    public enum CreditNoteStatus {
        PENDING("Pending"),
        ISSUED("Issued"),
        APPLIED("Applied"),
        VOIDED("Voided"),
        EXPIRED("Expired");

        private final String description;

        CreditNoteStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Credit note line value object.
     */
    public static final class CreditNoteLine implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String description;
        private final String invoiceLineId;
        private final Money amount;
        private final String taxCode;
        private final String reason;

        public CreditNoteLine(
                String description,
                String invoiceLineId,
                Money amount,
                String taxCode,
                String reason) {
            this.description = description;
            this.invoiceLineId = invoiceLineId;
            this.amount = amount;
            this.taxCode = taxCode;
            this.reason = reason;
            validate();
        }

        @Override
        public void validate() {
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("Description cannot be empty");
            }
            if (amount == null || amount.isZero()) {
                throw new IllegalArgumentException("Amount must be positive");
            }
        }

        public String getDescription() { return description; }
        public String getInvoiceLineId() { return invoiceLineId; }
        public Money getAmount() { return amount; }
        public String getTaxCode() { return taxCode; }
        public String getReason() { return reason; }

        @Override
        public String toString() {
            return "CreditNoteLine{" +
                    "description='" + description + '\'' +
                    ", amount=" + amount +
                    '}';
        }
    }
}