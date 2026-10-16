package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

/**
 * Command to record a payment failure.
 */
public record RecordPaymentFailureCommand(
        InvoiceId invoiceId,
        String reason,
        int attemptNumber
) implements Command {

    public RecordPaymentFailureCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason is required");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String reason;
        private int attemptNumber = 1;

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public RecordPaymentFailureCommand build() {
            return new RecordPaymentFailureCommand(invoiceId, reason, attemptNumber);
        }
    }
}