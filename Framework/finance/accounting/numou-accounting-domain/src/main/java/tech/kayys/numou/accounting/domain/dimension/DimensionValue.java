package tech.kayys.syirkah.accounting.domain.dimension;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import java.util.Objects;

/**
 * Value object representing a specific dimension tag attached to a journal line.
 */
public record DimensionValue(
        AccountingDimension dimension,
        String code,
        String name
) implements ValueObject {
    public DimensionValue {
        Objects.requireNonNull(dimension, "dimension cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
    }

    public static DimensionValue of(AccountingDimension dim, String code) {
        return new DimensionValue(dim, code, code);
    }

    public static DimensionValue of(AccountingDimension dim, String code, String name) {
        return new DimensionValue(dim, code, name);
    }
}
