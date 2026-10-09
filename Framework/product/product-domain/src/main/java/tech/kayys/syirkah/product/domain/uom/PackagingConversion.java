package tech.kayys.syirkah.product.domain.uom;

import java.util.Objects;

/**
 * Packaging composition relationship, e.g. 1 CASE = 24 BOTTLE or 1 BOX = 100 PCS.
 */
public record PackagingConversion(
        UnitOfMeasure from,
        UnitOfMeasure to,
        long unitsPerPackage
) {
    public PackagingConversion {
        Objects.requireNonNull(from, "from unit cannot be null");
        Objects.requireNonNull(to, "to unit cannot be null");

        if (from.equals(to)) {
            throw new IllegalArgumentException("Packaging conversion source and target units must be distinct");
        }

        if (unitsPerPackage <= 0) {
            throw new IllegalArgumentException("Units per package must be positive: " + unitsPerPackage);
        }
    }

    /**
     * Converts a package count to base units.
     */
    public long toUnits(long packageCount) {
        if (packageCount < 0) {
            throw new IllegalArgumentException("Package count cannot be negative");
        }
        return packageCount * unitsPerPackage;
    }
}
