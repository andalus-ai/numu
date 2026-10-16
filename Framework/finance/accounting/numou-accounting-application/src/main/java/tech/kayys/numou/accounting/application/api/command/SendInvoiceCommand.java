package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceDeliveryMethod;

/**
 * Command to send an invoice to the customer.
 */
public record SendInvoiceCommand(
        InvoiceId invoiceId,
        InvoiceDeliveryMethod deliveryMethod,
        String emailSubject,
        String emailBody,
        String templateId
) implements Command {

    public SendInvoiceCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
        if (deliveryMethod == null) {
            throw new IllegalArgumentException("Delivery method is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private InvoiceDeliveryMethod deliveryMethod = InvoiceDeliveryMethod.EMAIL;
        private String emailSubject;
        private String emailBody;
        private String templateId;

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder deliveryMethod(InvoiceDeliveryMethod deliveryMethod) {
            this.deliveryMethod = deliveryMethod;
            return this;
        }

        public Builder emailSubject(String emailSubject) {
            this.emailSubject = emailSubject;
            return this;
        }

        public Builder emailBody(String emailBody) {
            this.emailBody = emailBody;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public SendInvoiceCommand build() {
            return new SendInvoiceCommand(invoiceId, deliveryMethod, emailSubject, emailBody, templateId);
        }
    }
}