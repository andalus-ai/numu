package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Status of an invoice.
 */
public enum InvoiceStatus implements ValueObject {
    DRAFT("Draft - being created"),
    SENT("Sent - delivered to customer"),
    VIEWED("Viewed - customer has seen it"),
    PARTIALLY_PAID("Partially Paid - some payment received"),
    PAID("Paid - fully paid"),
    OVERDUE("Overdue - payment past due"),
    CANCELLED("Cancelled - voided"),
    REFUNDED("Refunded - money returned"),
    WRITTEN_OFF("Written Off - considered uncollectable");

    private final String description;

    InvoiceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPaid() {
        return this == PAID;
    }

    public boolean isOutstanding() {
        return this == SENT || this == VIEWED || this == PARTIALLY_PAID || this == OVERDUE;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == PAID || this == REFUNDED || this == WRITTEN_OFF;
    }

    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT -> target == SENT || target == CANCELLED;
            case SENT, VIEWED -> target == PARTIALLY_PAID || target == PAID ||
                    target == OVERDUE || target == CANCELLED;
            case PARTIALLY_PAID -> target == PAID || target == OVERDUE || target == CANCELLED;
            case OVERDUE -> target == PARTIALLY_PAID || target == PAID ||
                    target == WRITTEN_OFF || target == CANCELLED;
            case PAID, REFUNDED, CANCELLED, WRITTEN_OFF -> false;
        };
    }
}
