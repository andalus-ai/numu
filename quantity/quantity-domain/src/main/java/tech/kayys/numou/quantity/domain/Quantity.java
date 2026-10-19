package tech.kayys.numou.quantity.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Non-negative measured quantity with an explicit unit. */
public record Quantity(BigDecimal value, UnitOfMeasure unit) {
    public Quantity {
        Objects.requireNonNull(value, "Quantity value cannot be null");
        Objects.requireNonNull(unit, "Quantity unit cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }

    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "Quantity to add cannot be null");
        if (!unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Cannot add quantities with different units: " + unit.code() + " and " + other.unit.code());
        }
        return new Quantity(value.add(other.value), unit);
    }

    public Quantity multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Quantity factor cannot be null");
        if (factor.signum() < 0) {
            throw new IllegalArgumentException("Quantity factor cannot be negative");
        }
        return new Quantity(value.multiply(factor), unit);
    }
}
