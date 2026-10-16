package tech.kayys.numou.foundation.domain.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Measurable domain quantity.
 *
 * Cross-unit conversion (kg + g, liter + ml) intentionally does not
 * live here - that belongs to a UnitConversionService in the
 * Inventory/UOM bounded context.
 */
public record Quantity(
        BigDecimal value,
        Unit unit
) implements ValueObject {

    public Quantity {
        Objects.requireNonNull(
                value,
                "Quantity value cannot be null"
        );

        Objects.requireNonNull(
                unit,
                "Quantity unit cannot be null"
        );

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot be negative"
            );
        }
    }

    public static Quantity of(
            BigDecimal value,
            Unit unit
    ) {
        return new Quantity(value, unit);
    }

    public static Quantity of(
            long value,
            Unit unit
    ) {
        return new Quantity(
                BigDecimal.valueOf(value),
                unit
        );
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public Quantity add(Quantity other) {
        requireSameUnit(other);

        return new Quantity(
                value.add(other.value),
                unit
        );
    }

    public Quantity subtract(Quantity other) {
        requireSameUnit(other);

        var result = value.subtract(other.value);

        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot become negative"
            );
        }

        return new Quantity(result, unit);
    }

    public Quantity multiply(BigDecimal multiplier) {
        Objects.requireNonNull(
                multiplier,
                "Multiplier cannot be null"
        );

        var result = value.multiply(multiplier);

        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "Quantity cannot become negative"
            );
        }

        return new Quantity(result, unit);
    }

    private void requireSameUnit(Quantity other) {
        Objects.requireNonNull(
                other,
                "Quantity cannot be null"
        );

        if (!unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Unit mismatch: "
                            + unit.code()
                            + " vs "
                            + other.unit.code()
            );
        }
    }

}
