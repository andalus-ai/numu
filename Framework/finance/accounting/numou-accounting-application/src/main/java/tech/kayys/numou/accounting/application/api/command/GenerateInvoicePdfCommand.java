package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;

/**
 * Command to generate a PDF for an invoice.
 */
public record GenerateInvoicePdfCommand(
        InvoiceId invoiceId,
        String templateId,
        String language
) implements Command {

    public GenerateInvoicePdfCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("Invoice ID cannot be null");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private String templateId;
        private String language = "en";

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public GenerateInvoicePdfCommand build() {
            return new GenerateInvoicePdfCommand(invoiceId, templateId, language);
        }
    }
}