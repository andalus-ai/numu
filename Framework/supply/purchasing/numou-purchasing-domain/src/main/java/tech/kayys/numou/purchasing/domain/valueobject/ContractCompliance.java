package tech.kayys.syirkah.purchasing.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.util.Objects;

/**
 * Contract compliance record.
 */
public final class ContractCompliance implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String complianceType;
    private final String description;
    private final Instant reviewDate;
    private final Instant nextReviewDate;
    private final String reviewer;
    private final String status;
    private final String findings;
    private final boolean compliant;
    private final String recommendations;

    public ContractCompliance(
            String complianceType,
            String description,
            Instant reviewDate,
            Instant nextReviewDate,
            String reviewer,
            String status,
            String findings,
            boolean compliant,
            String recommendations) {
        this.complianceType = complianceType;
        this.description = description;
        this.reviewDate = reviewDate;
        this.nextReviewDate = nextReviewDate;
        this.reviewer = reviewer;
        this.status = status;
        this.findings = findings;
        this.compliant = compliant;
        this.recommendations = recommendations;
        validate();
    }

    @Override
    public void validate() {
        if (complianceType == null || complianceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Compliance type cannot be empty");
        }
        if (reviewDate == null) {
            throw new IllegalArgumentException("Review date cannot be null");
        }
        if (reviewer == null || reviewer.trim().isEmpty()) {
            throw new IllegalArgumentException("Reviewer cannot be empty");
        }
        if (nextReviewDate != null && nextReviewDate.isBefore(reviewDate)) {
            throw new IllegalArgumentException("Next review date must be after review date");
        }
    }

    public String getComplianceType() { return complianceType; }
    public String getDescription() { return description; }
    public Instant getReviewDate() { return reviewDate; }
    public Instant getNextReviewDate() { return nextReviewDate; }
    public String getReviewer() { return reviewer; }
    public String getStatus() { return status; }
    public String getFindings() { return findings; }
    public boolean isCompliant() { return compliant; }
    public String getRecommendations() { return recommendations; }

    public boolean requiresFollowUp() {
        return !compliant || (nextReviewDate != null && Instant.now().isAfter(nextReviewDate));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractCompliance that = (ContractCompliance) o;
        return Objects.equals(complianceType, that.complianceType) &&
               Objects.equals(reviewDate, that.reviewDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(complianceType, reviewDate);
    }

    @Override
    public String toString() {
        return "ContractCompliance{" +
                "complianceType='" + complianceType + '\'' +
                ", reviewDate=" + reviewDate +
                ", compliant=" + compliant +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String complianceType;
        private String description;
        private Instant reviewDate;
        private Instant nextReviewDate;
        private String reviewer;
        private String status = "Review Complete";
        private String findings;
        private boolean compliant = true;
        private String recommendations;

        public Builder complianceType(String complianceType) {
            this.complianceType = complianceType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder reviewDate(Instant reviewDate) {
            this.reviewDate = reviewDate;
            return this;
        }

        public Builder nextReviewDate(Instant nextReviewDate) {
            this.nextReviewDate = nextReviewDate;
            return this;
        }

        public Builder reviewer(String reviewer) {
            this.reviewer = reviewer;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder findings(String findings) {
            this.findings = findings;
            return this;
        }

        public Builder compliant(boolean compliant) {
            this.compliant = compliant;
            return this;
        }

        public Builder recommendations(String recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public ContractCompliance build() {
            if (reviewDate == null) {
                reviewDate = Instant.now();
            }
            return new ContractCompliance(
                complianceType, description, reviewDate, nextReviewDate,
                reviewer, status, findings, compliant, recommendations
            );
        }
    }
}
