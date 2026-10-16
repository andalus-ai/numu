package tech.kayys.syirkah.product.domain.uom;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Product-specific unit policy defining base unit, permitted sales/purchase units, and packaging.
 */
public record ProductUnitPolicy(
        String productId,
        UnitOfMeasure baseUnit,
        Set<UnitOfMeasure> permittedSalesUnits,
        Set<UnitOfMeasure> permittedPurchaseUnits,
        Set<PackagingConversion> packagingConversions
) {
    public ProductUnitPolicy {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(baseUnit, "baseUnit cannot be null");

        productId = productId.trim();
        if (productId.isEmpty()) {
            throw new IllegalArgumentException("productId cannot be blank");
        }

        permittedSalesUnits = permittedSalesUnits == null ? Set.of(baseUnit) : Set.copyOf(permittedSalesUnits);
        permittedPurchaseUnits = permittedPurchaseUnits == null ? Set.of(baseUnit) : Set.copyOf(permittedPurchaseUnits);
        packagingConversions = packagingConversions == null ? Set.of() : Set.copyOf(packagingConversions);
    }

    public boolean isSalesUnitPermitted(UnitOfMeasure unit) {
        return permittedSalesUnits.contains(unit);
    }

    public boolean isPurchaseUnitPermitted(UnitOfMeasure unit) {
        return permittedPurchaseUnits.contains(unit);
    }
}
