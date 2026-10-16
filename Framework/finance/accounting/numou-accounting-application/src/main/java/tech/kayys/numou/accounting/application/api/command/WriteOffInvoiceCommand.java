package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

/**
 * Command to write off an invoice as uncollectable.
 */
public record WriteOffInvoiceCommand(
        InvoiceId invoiceId,
        String reason,
        String writeOffAccount
) implements Command {

    public WriteOffInvoiceCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Write-off reason is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String reason;
        private String writeOffAccount = "BAD_DEBT_EXPENSE";

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder writeOffAccount(String writeOffAccount) {
            this.writeOffAccount = writeOffAccount;
            return this;
        }

        public WriteOffInvoiceCommand build() {
            return new WriteOffInvoiceCommand(invoiceId, reason, writeOffAccount);
        }
    }
}