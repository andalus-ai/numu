package tech.kayys.syirkah.purchasing.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.util.Objects;

/**
 * Contract amendment value object.
 */
public final class ContractAmendment implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String amendmentNumber;
    private final String description;
    private final String changeType;
    private final String beforeChange;
    private final String afterChange;
    private final Instant effectiveDate;
    private final String approvedBy;
    private final Instant approvedAt;
    private final String notes;

    public ContractAmendment(
            String amendmentNumber,
            String description,
            String changeType,
            String beforeChange,
            String afterChange,
            Instant effectiveDate,
            String approvedBy,
            Instant approvedAt,
            String notes) {
        this.amendmentNumber = amendmentNumber;
        this.description = description;
        this.changeType = changeType;
        this.beforeChange = beforeChange;
        this.afterChange = afterChange;
        this.effectiveDate = effectiveDate;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.notes = notes;
        validate();
    }

    @Override
    public void validate() {
        if (amendmentNumber == null || amendmentNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Amendment number cannot be empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }
        if (changeType == null || changeType.trim().isEmpty()) {
            throw new IllegalArgumentException("Change type cannot be empty");
        }
        if (effectiveDate == null) {
            throw new IllegalArgumentException("Effective date cannot be null");
        }
        if (approvedBy == null || approvedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Approved by cannot be empty");
        }
        if (approvedAt == null) {
            throw new IllegalArgumentException("Approved at cannot be null");
        }
    }

    public String getAmendmentNumber() { return amendmentNumber; }
    public String getDescription() { return description; }
    public String getChangeType() { return changeType; }
    public String getBeforeChange() { return beforeChange; }
    public String getAfterChange() { return afterChange; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getNotes() { return notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractAmendment that = (ContractAmendment) o;
        return Objects.equals(amendmentNumber, that.amendmentNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amendmentNumber);
    }

    @Override
    public String toString() {
        return "ContractAmendment{" +
                "amendmentNumber='" + amendmentNumber + '\'' +
                ", description='" + description + '\'' +
                ", effectiveDate=" + effectiveDate +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String amendmentNumber;
        private String description;
        private String changeType;
        private String beforeChange;
        private String afterChange;
        private Instant effectiveDate;
        private String approvedBy;
        private Instant approvedAt;
        private String notes;

        public Builder amendmentNumber(String amendmentNumber) {
            this.amendmentNumber = amendmentNumber;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder changeType(String changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder beforeChange(String beforeChange) {
            this.beforeChange = beforeChange;
            return this;
        }

        public Builder afterChange(String afterChange) {
            this.afterChange = afterChange;
            return this;
        }

        public Builder effectiveDate(Instant effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder approvedAt(Instant approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public ContractAmendment build() {
            if (approvedAt == null) {
                approvedAt = Instant.now();
            }
            return new ContractAmendment(
                amendmentNumber, description, changeType, beforeChange,
                afterChange, effectiveDate, approvedBy, approvedAt, notes
            );
        }
    }
}
