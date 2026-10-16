package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

/**
 * Command to cancel an invoice.
 */
public record CancelInvoiceCommand(
        InvoiceId invoiceId,
        String reason
) implements Command {

    public CancelInvoiceCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String reason;

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public CancelInvoiceCommand build() {
            return new CancelInvoiceCommand(invoiceId, reason);
        }
    }
}