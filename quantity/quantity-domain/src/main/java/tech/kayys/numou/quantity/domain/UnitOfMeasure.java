package tech.kayys.numou.quantity.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable unit code for a measured quantity.
 *
 * Conversion factors are deliberately not inferred from labels; conversion
 * belongs in an explicit, versioned unit registry when required.
 */
public record UnitOfMeasure(String code) {
    public UnitOfMeasure {
        Objects.requireNonNull(code, "Unit code cannot be null");
        code = code.trim().toLowerCase(Locale.ROOT);
        if (code.isBlank()) {
            throw new IllegalArgumentException("Unit code cannot be blank");
        }
    }
}
