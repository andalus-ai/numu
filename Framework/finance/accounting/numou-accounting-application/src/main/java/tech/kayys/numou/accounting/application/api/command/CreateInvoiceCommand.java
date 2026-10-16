package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command to create a new invoice.
 */
public record CreateInvoiceCommand(
        InvoiceId invoiceId,
        UUID customerId,
        String invoiceNumber,
        Instant dueDate,
        List<InvoiceLineCommand> lines,
        String customerNotes,
        String purchaseOrderNumber,
        String currencyCode
) implements Command {

    public CreateInvoiceCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line");
        }
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency code is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceId invoiceId;
        private UUID customerId;
        private String invoiceNumber;
        private Instant dueDate;
        private List<InvoiceLineCommand> lines;
        private String customerNotes;
        private String purchaseOrderNumber;
        private String currencyCode = "USD";

        public Builder invoiceId(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder customerId(UUID customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public Builder dueDate(Instant dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder lines(List<InvoiceLineCommand> lines) {
            this.lines = lines;
            return this;
        }

        public Builder customerNotes(String customerNotes) {
            this.customerNotes = customerNotes;
            return this;
        }

        public Builder purchaseOrderNumber(String purchaseOrderNumber) {
            this.purchaseOrderNumber = purchaseOrderNumber;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public CreateInvoiceCommand build() {
            if (invoiceId == null) {
                invoiceId = InvoiceId.generate();
            }
            if (dueDate == null) {
                dueDate = Instant.now().plusSeconds(30L * 24L * 60L * 60L); // 30 days
            }
            return new CreateInvoiceCommand(
                invoiceId, customerId, invoiceNumber, dueDate,
                lines, customerNotes, purchaseOrderNumber, currencyCode
            );
        }
    }

    /**
     * Invoice line command.
     */
    public record InvoiceLineCommand(
            UUID productId,
            String description,
            int quantity,
            String unitPrice,
            String taxRate,
            String discountRate
    ) {
        public InvoiceLineCommand {
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("Description cannot be empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            if (unitPrice == null || unitPrice.trim().isEmpty()) {
                throw new IllegalArgumentException("Unit price is required");
            }
        }
    }
}