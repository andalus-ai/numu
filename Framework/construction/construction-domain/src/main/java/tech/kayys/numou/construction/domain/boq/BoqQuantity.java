package tech.kayys.syirkah.construction.domain.boq;

import java.math.BigDecimal;
import java.util.Objects;

public record BoqQuantity(BigDecimal value, String unit) {
    public BoqQuantity {
        Objects.requireNonNull(value, "Quantity cannot be null");
        if (value.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Unit cannot be blank");
    }
}
