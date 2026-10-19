package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.LeadId;

/**
 * Command to convert a lead to a customer.
 */
public record ConvertLeadCommand(
        LeadId leadId,
        String currencyCode,
        String paymentTerms,
        String creditLimit
) implements Command {

    public ConvertLeadCommand {
        if (leadId == null) {
            throw new IllegalArgumentException("Lead ID cannot be null");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LeadId leadId;
        private String currencyCode = "USD";
        private String paymentTerms;
        private String creditLimit;

        public Builder leadId(LeadId leadId) {
            this.leadId = leadId;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder paymentTerms(String paymentTerms) {
            this.paymentTerms = paymentTerms;
            return this;
        }

        public Builder creditLimit(String creditLimit) {
            this.creditLimit = creditLimit;
            return this;
        }

        public ConvertLeadCommand build() {
            return new ConvertLeadCommand(leadId, currencyCode, paymentTerms, creditLimit);
        }
    }
}