package tech.kayys.numou.foundation.domain.valueobject;

import java.util.Objects;

/**
 * Unit of measurement (e.g. kg, g, l, ml, pcs, box).
 *
 * Deliberately not an enum - the real ERP unit-of-measure catalog plus
 * conversion rules belongs in the Inventory/UOM bounded context, not
 * in this shared primitive.
 */
public record Unit(String code)
        implements ValueObject {

    public Unit {
        Objects.requireNonNull(
                code,
                "Unit code cannot be null"
        );

        code = code.trim();

        if (code.isBlank()) {
            throw new IllegalArgumentException(
                    "Unit code cannot be blank"
            );
        }
    }

    public static Unit of(String code) {
        return new Unit(code);
    }

}
