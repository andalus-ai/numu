package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

/**
 * Command to refund an invoice.
 */
public record RefundInvoiceCommand(
        InvoiceId invoiceId,
        String amount,
        String currencyCode,
        String reason,
        String reference
) implements Command {

    public RefundInvoiceCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (amount == null || amount.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Refund reason is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String amount;
        private String currencyCode = "USD";
        private String reason;
        private String reference;

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder amount(String amount) {
            this.amount = amount;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public RefundInvoiceCommand build() {
            return new RefundInvoiceCommand(invoiceId, amount, currencyCode, reason, reference);
        }
    }
}