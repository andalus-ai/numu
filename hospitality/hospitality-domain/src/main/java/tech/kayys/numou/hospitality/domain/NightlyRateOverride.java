package tech.kayys.numou.hospitality.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A date-specific nightly amount override for exactly one rate plan and stay night. */
public record NightlyRateOverride(UUID ratePlanId, LocalDate date, BigDecimal amount) {
    public NightlyRateOverride {
        Objects.requireNonNull(ratePlanId, "ratePlanId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount must not be negative");
    }
}
