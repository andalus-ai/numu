package tech.kayys.syirkah.product.domain.uom;

/**
 * A named unit of measure (e.g. KG/WEIGHT, BOX/COUNT).
 *
 * Code is normalized to upper case: "kg" and "KG" are the same
 * unit.
 */
public record UnitOfMeasure(
        String code,
        String name,
        UnitCategory category
) {

    public UnitOfMeasure {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "UOM code cannot be blank"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "UOM name cannot be blank"
            );
        }

        if (category == null) {
            throw new IllegalArgumentException(
                    "UOM category cannot be null"
            );
        }

        code = code.trim().toUpperCase();
        name = name.trim();
    }
}