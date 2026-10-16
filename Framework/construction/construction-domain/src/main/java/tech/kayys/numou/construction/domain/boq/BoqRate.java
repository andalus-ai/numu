package tech.kayys.syirkah.construction.domain.boq;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record BoqRate(BigDecimal amount, Currency currency, String unit) {
    public BoqRate {
        Objects.requireNonNull(amount, "Rate amount cannot be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("Rate cannot be negative");
        Objects.requireNonNull(currency, "Currency cannot be null");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Rate unit cannot be blank");
    }
}
