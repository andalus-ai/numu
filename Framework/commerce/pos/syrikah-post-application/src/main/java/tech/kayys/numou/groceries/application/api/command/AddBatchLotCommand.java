package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;

import java.time.Instant;

/**
 * Command to add a batch/lot to a pos product.
 */
public record AddBatchLotCommand(
        ProductId productId,
        String batchNumber,
        Instant productionDate,
        Instant expiryDate,
        int quantity,
        String supplierName,
        String supplierLotNumber
) implements Command {

    public AddBatchLotCommand {
        if (productId == null) {
            throw new IllegalArgumentException(" product ID cannot be null");
        }
        if (batchNumber == null || batchNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Batch number cannot be empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductId productId;
        private String batchNumber;
        private Instant productionDate;
        private Instant expiryDate;
        private int quantity;
        private String supplierName;
        private String supplierLotNumber;

        public Builder productId(ProductId productId) {
            this.productId = productId;
            return this;
        }

        public Builder batchNumber(String batchNumber) {
            this.batchNumber = batchNumber;
            return this;
        }

        public Builder productionDate(Instant productionDate) {
            this.productionDate = productionDate;
            return this;
        }

        public Builder expiryDate(Instant expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder supplierName(String supplierName) {
            this.supplierName = supplierName;
            return this;
        }

        public Builder supplierLotNumber(String supplierLotNumber) {
            this.supplierLotNumber = supplierLotNumber;
            return this;
        }

        public AddBatchLotCommand build() {
            if (productionDate == null) {
                productionDate = Instant.now();
            }
            return new AddBatchLotCommand(
                productId, batchNumber, productionDate,
                expiryDate, quantity, supplierName, supplierLotNumber
            );
        }
    }
}
