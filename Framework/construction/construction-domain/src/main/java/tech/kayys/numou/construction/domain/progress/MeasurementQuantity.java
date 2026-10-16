package tech.kayys.syirkah.construction.domain.progress;

import java.math.BigDecimal;
import java.util.Objects;

public record MeasurementQuantity(BigDecimal quantity, String unit) {
    public MeasurementQuantity {
        Objects.requireNonNull(quantity, "Measurement quantity cannot be null");
        Objects.requireNonNull(unit, "Unit cannot be null");
        if (quantity.signum() < 0) throw new IllegalArgumentException("Measurement quantity cannot be negative");
        if (unit.isBlank()) throw new IllegalArgumentException("Unit cannot be blank");
    }
}
