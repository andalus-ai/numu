package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Invoice history record.
 */
public final class InvoiceHistory implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final String action;
    private final InvoiceStatus fromStatus;
    private final InvoiceStatus toStatus;
    private final String performedBy;
    private final Instant performedAt;
    private final String notes;

    public InvoiceHistory(
            String action,
            InvoiceStatus fromStatus,
            InvoiceStatus toStatus,
            String performedBy,
            Instant performedAt,
            String notes) {
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.performedBy = performedBy;
        this.performedAt = performedAt != null ? performedAt : Instant.now();
        this.notes = notes;
        validate();
    }

    public void validate() {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be empty");
        }
        if (performedBy == null || performedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Performed by cannot be empty");
        }
    }

    public String getAction() { return action; }
    public InvoiceStatus getFromStatus() { return fromStatus; }
    public InvoiceStatus getToStatus() { return toStatus; }
    public String getPerformedBy() { return performedBy; }
    public Instant getPerformedAt() { return performedAt; }
    public String getNotes() { return notes; }

    public boolean isStatusChange() {
        return fromStatus != null && toStatus != null && fromStatus != toStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceHistory that = (InvoiceHistory) o;
        return Objects.equals(action, that.action) &&
               Objects.equals(performedAt, that.performedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, performedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String action;
        private InvoiceStatus fromStatus;
        private InvoiceStatus toStatus;
        private String performedBy;
        private Instant performedAt;
        private String notes;

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder fromStatus(InvoiceStatus fromStatus) {
            this.fromStatus = fromStatus;
            return this;
        }

        public Builder toStatus(InvoiceStatus toStatus) {
            this.toStatus = toStatus;
            return this;
        }

        public Builder performedBy(String performedBy) {
            this.performedBy = performedBy;
            return this;
        }

        public Builder performedAt(Instant performedAt) {
            this.performedAt = performedAt;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public InvoiceHistory build() {
            return new InvoiceHistory(action, fromStatus, toStatus, performedBy, performedAt, notes);
        }
    }
}