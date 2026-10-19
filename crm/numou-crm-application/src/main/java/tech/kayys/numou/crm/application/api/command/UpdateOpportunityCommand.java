package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;

/**
 * Command to update an opportunity.
 */
public record UpdateOpportunityCommand(
        OpportunityId opportunityId,
        String name,
        String description,
        double estimatedValue,
        String currencyCode,
        UserId assignedTo,
        Instant expectedCloseDate,
        String leadSource,
        String productInterest,
        String competitors,
        String decisionCriteria,
        String nextStep,
        String notes
) implements Command {

    public UpdateOpportunityCommand {
        if (opportunityId == null) {
            throw new IllegalArgumentException("Opportunity ID cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Opportunity name cannot be empty");
        }
        if (estimatedValue <= 0) {
            throw new IllegalArgumentException("Estimated value must be positive");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private OpportunityId opportunityId;
        private String name;
        private String description;
        private double estimatedValue;
        private String currencyCode = "USD";
        private UserId assignedTo;
        private Instant expectedCloseDate;
        private String leadSource;
        private String productInterest;
        private String competitors;
        private String decisionCriteria;
        private String nextStep;
        private String notes;

        public Builder opportunityId(OpportunityId opportunityId) {
            this.opportunityId = opportunityId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder estimatedValue(double estimatedValue) {
            this.estimatedValue = estimatedValue;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder assignedTo(UserId assignedTo) {
            this.assignedTo = assignedTo;
            return this;
        }

        public Builder expectedCloseDate(Instant expectedCloseDate) {
            this.expectedCloseDate = expectedCloseDate;
            return this;
        }

        public Builder leadSource(String leadSource) {
            this.leadSource = leadSource;
            return this;
        }

        public Builder productInterest(String productInterest) {
            this.productInterest = productInterest;
            return this;
        }

        public Builder competitors(String competitors) {
            this.competitors = competitors;
            return this;
        }

        public Builder decisionCriteria(String decisionCriteria) {
            this.decisionCriteria = decisionCriteria;
            return this;
        }

        public Builder nextStep(String nextStep) {
            this.nextStep = nextStep;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public UpdateOpportunityCommand build() {
            return new UpdateOpportunityCommand(
                opportunityId, name, description, estimatedValue,
                currencyCode, assignedTo, expectedCloseDate,
                leadSource, productInterest, competitors,
                decisionCriteria, nextStep, notes
            );
        }
    }
}