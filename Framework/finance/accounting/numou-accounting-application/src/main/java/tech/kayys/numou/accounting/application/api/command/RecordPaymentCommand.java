package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.accounting.domain.valueobject.PaymentMethod;
import tech.kayys.syirkah.foundation.application.command.Command;

/**
 * Command to record a payment against an invoice.
 */
public record RecordPaymentCommand(
        InvoiceId invoiceId,
        String amount,
        String currencyCode,
        PaymentMethod paymentMethod,
        String reference,
        String transactionId
) implements Command {

    public RecordPaymentCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (amount == null || amount.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code is required");
        }
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String amount;
        private String currencyCode = "USD";
        private PaymentMethod paymentMethod;
        private String reference;
        private String transactionId;

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

        public Builder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public RecordPaymentCommand build() {
            return new RecordPaymentCommand(
                invoiceId, amount, currencyCode, paymentMethod, reference, transactionId
            );
        }
    }
}