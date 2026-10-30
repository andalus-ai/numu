package tech.kayys.numou.hospitality.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A sellable accommodation rate plan. The validity interval is inclusive at the
 * start and exclusive at the end, matching Hospitality's stay-night convention.
 *
 * <p>This is a pricing policy, not a reservation or inventory record. A resolved
 * nightly amount is copied into the immutable StayQuote before checkout.</p>
 */
public record StayRatePlan(UUID id, UUID listingId, UUID unitId, String code, String name,
                           String currency, BigDecimal baseNightlyRate,
                           LocalDate validFrom, LocalDate validUntil, boolean enabled) {
    public StayRatePlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        code = requireText(code, "code");
        name = requireText(name, "name");
        try {
            currency = Currency.getInstance(requireText(currency, "currency")).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency must be a supported ISO-4217 code", exception);
        }
        baseNightlyRate = requireNonNegative(baseNightlyRate, "baseNightlyRate");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
        if (!validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
    }

    /** Resolves one occupied night, applying a matching date override when present. */
    public BigDecimal nightlyRate(LocalDate night, Optional<NightlyRateOverride> override) {
        Objects.requireNonNull(night, "night");
        Objects.requireNonNull(override, "override");
        if (!enabled) throw new IllegalStateException("rate plan is disabled");
        if (night.isBefore(validFrom) || !night.isBefore(validUntil)) {
            throw new IllegalArgumentException("night is outside the rate plan validity interval");
        }
        if (override.isEmpty()) return baseNightlyRate;
        NightlyRateOverride value = override.get();
        if (!id.equals(value.ratePlanId()) || !night.equals(value.date())) {
            throw new IllegalArgumentException("nightly rate override does not belong to this plan and date");
        }
        return value.amount();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
