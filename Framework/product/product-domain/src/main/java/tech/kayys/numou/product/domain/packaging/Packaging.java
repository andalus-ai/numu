package tech.kayys.syirkah.product.domain.packaging;

import tech.kayys.syirkah.product.domain.uom.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One packaging level of a product, e.g. Mineral Water:
 * BOX = 12 PCS, CARTON = 48 PCS, PALLET = 60 CARTON.
 *
 * Packaging is NOT a SKU and owns no stock - inventory and
 * logistics consume it.
 */
public record Packaging(
        String code,
        String name,
        UnitOfMeasure unit,
        BigDecimal quantity
) {

    public Packaging {
        Objects.requireNonNull(unit, "unit cannot be null");
        Objects.requireNonNull(
                quantity,
                "quantity cannot be null"
        );

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Packaging code cannot be blank"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Packaging name cannot be blank"
            );
        }

        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Packaging quantity must be positive"
            );
        }

        code = code.trim();
        name = name.trim();
    }
}