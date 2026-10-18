package tech.kayys.numou.construction.domain.boq;

import tech.kayys.numou.quantity.domain.Quantity;
import tech.kayys.numou.quantity.domain.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.Objects;

/** Construction BOQ facade over the reusable, commercially neutral quantity primitive. */
public record BoqQuantity(BigDecimal value, String unit) {
    public BoqQuantity {
        Objects.requireNonNull(value, "Quantity cannot be null");
        if (value.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Unit cannot be blank");
    }

    public Quantity toQuantity() {
        return new Quantity(value, new UnitOfMeasure(unit));
    }

    public static BoqQuantity from(Quantity quantity) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        return new BoqQuantity(quantity.value(), quantity.unit().code());
    }
}
