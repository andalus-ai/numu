package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ProductId;
import tech.kayys.syirkah.groceries.domain.valueobject.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Command to register a pos product.
 */
public record RegisterProductCommand(
        ProductId productId,
        UUID catalogProductId,
        ProductType productType,
        boolean isWeightBased,
        BigDecimal defaultWeightKg,
        BigDecimal minWeightKg,
        BigDecimal maxWeightKg,
        Instant productionDate,
        Instant expiryDate,
        int shelfLifeDays,
        String temperatureRange,
        String storageInstructions,
        String handlingInstructions,
        List<String> allergens,
        boolean organic,
        boolean glutenFree,
        boolean vegan,
        boolean kosher,
        boolean halal,
        String countryOfOrigin,
        String supplierBatchNumber
) implements Command {

    public RegisterProductCommand {
        if (catalogProductId == null) {
            throw new IllegalArgumentException("Catalog product ID cannot be null");
        }
        if (productType == null) {
            throw new IllegalArgumentException("Product type is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductId productId;
        private UUID catalogProductId;
        private ProductType productType;
        private boolean isWeightBased = false;
        private BigDecimal defaultWeightKg;
        private BigDecimal minWeightKg;
        private BigDecimal maxWeightKg;
        private Instant productionDate;
        private Instant expiryDate;
        private int shelfLifeDays;
        private String temperatureRange;
        private String storageInstructions;
        private String handlingInstructions;
        private List<String> allergens;
        private boolean organic = false;
        private boolean glutenFree = false;
        private boolean vegan = false;
        private boolean kosher = false;
        private boolean halal = false;
        private String countryOfOrigin;
        private String supplierBatchNumber;

        public Builder productId(ProductId productId) {
            this.productId = productId;
            return this;
        }

        public Builder catalogProductId(UUID catalogProductId) {
            this.catalogProductId = catalogProductId;
            return this;
        }

        public Builder productType(ProductType productType) {
            this.productType = productType;
            return this;
        }

        public Builder isWeightBased(boolean isWeightBased) {
            this.isWeightBased = isWeightBased;
            return this;
        }

        public Builder defaultWeightKg(BigDecimal defaultWeightKg) {
            this.defaultWeightKg = defaultWeightKg;
            return this;
        }

        public Builder minWeightKg(BigDecimal minWeightKg) {
            this.minWeightKg = minWeightKg;
            return this;
        }

        public Builder maxWeightKg(BigDecimal maxWeightKg) {
            this.maxWeightKg = maxWeightKg;
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

        public Builder temperatureRange(String temperatureRange) {
            this.temperatureRange = temperatureRange;
            return this;
        }

        public Builder storageInstructions(String storageInstructions) {
            this.storageInstructions = storageInstructions;
            return this;
        }

        public Builder handlingInstructions(String handlingInstructions) {
            this.handlingInstructions = handlingInstructions;
            return this;
        }

        public Builder allergens(List<String> allergens) {
            this.allergens = allergens;
            return this;
        }

        public Builder organic(boolean organic) {
            this.organic = organic;
            return this;
        }

        public Builder glutenFree(boolean glutenFree) {
            this.glutenFree = glutenFree;
            return this;
        }

        public Builder vegan(boolean vegan) {
            this.vegan = vegan;
            return this;
        }

        public Builder kosher(boolean kosher) {
            this.kosher = kosher;
            return this;
        }

        public Builder halal(boolean halal) {
            this.halal = halal;
            return this;
        }

        public Builder countryOfOrigin(String countryOfOrigin) {
            this.countryOfOrigin = countryOfOrigin;
            return this;
        }

        public Builder supplierBatchNumber(String supplierBatchNumber) {
            this.supplierBatchNumber = supplierBatchNumber;
            return this;
        }

        public RegisterProductCommand build() {
            if (productId == null) {
                productId = ProductId.generate();
            }
            return new RegisterProductCommand(
                productId, catalogProductId, productType, isWeightBased,
                defaultWeightKg, minWeightKg, maxWeightKg,
                productionDate, expiryDate, shelfLifeDays,
                temperatureRange, storageInstructions, handlingInstructions,
                allergens, organic, glutenFree, vegan, kosher, halal,
                countryOfOrigin, supplierBatchNumber
            );
        }
    }
}
