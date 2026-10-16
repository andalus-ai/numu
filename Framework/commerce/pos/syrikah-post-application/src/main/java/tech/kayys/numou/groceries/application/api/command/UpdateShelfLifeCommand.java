package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;

import java.time.Instant;

/**
 * Command to update shelf life of a pos product.
 */
public record UpdateShelfLifeCommand(
        ProductId productId,
        Instant productionDate,
        Instant expiryDate,
        int shelfLifeDays
) implements Command {

    public UpdateShelfLifeCommand {
        if (productId == null) {
            throw new IllegalArgumentException(" product ID cannot be null");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductId productId;
        private Instant productionDate;
        private Instant expiryDate;
        private int shelfLifeDays;

        public Builder productId(ProductId productId) {
            this.productId = productId;
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

        public Builder shelfLifeDays(int shelfLifeDays) {
            this.shelfLifeDays = shelfLifeDays;
            return this;
        }

        public UpdateShelfLifeCommand build() {
            return new UpdateShelfLifeCommand(
                productId, productionDate, expiryDate, shelfLifeDays
            );
        }
    }
}
