package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to create a new opportunity.
 */
public record CreateOpportunityCommand(
        OpportunityId opportunityId,
        String name,
        String description,
        UUID customerId,
        String customerName,
        OpportunityStage stage,
        double estimatedValue,
        String currencyCode,
        UserId assignedTo,
        Instant expectedCloseDate,
        String leadSource,
        String productInterest,
        String notes
) implements Command {

    public CreateOpportunityCommand {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Opportunity name cannot be empty");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (stage == null) {
            throw new IllegalArgumentException("Stage cannot be null");
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
        private UUID customerId;
        private String customerName;
        private OpportunityStage stage = OpportunityStage.PROSPECTING;
        private double estimatedValue;
        private String currencyCode = "USD";
        private UserId assignedTo;
        private Instant expectedCloseDate;
        private String leadSource;
        private String productInterest;
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

        public Builder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder customerName(String customerName) {
            this.customerName = customerName;
            return this;
        }

        public Builder stage(OpportunityStage stage) {
            this.stage = stage;
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

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public CreateOpportunityCommand build() {
            if (opportunityId == null) {
                opportunityId = OpportunityId.generate();
            }
            if (expectedCloseDate == null) {
                expectedCloseDate = Instant.now().plusSeconds(30L * 24L * 60L * 60L);
            }
            return new CreateOpportunityCommand(
                opportunityId, name, description, customerId, customerName,
                stage, estimatedValue, currencyCode, assignedTo,
                expectedCloseDate, leadSource, productInterest, notes
            );
        }
    }
}