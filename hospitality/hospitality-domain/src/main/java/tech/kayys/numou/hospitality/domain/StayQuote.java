package tech.kayys.numou.hospitality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable price snapshot for a stay. Checkout is exclusive: nights equals days between
 * check-in and check-out. Tax/fee calculation is supplied by application policy; this
 * value object validates and freezes the result.
 */
public record StayQuote(UUID id, UUID listingId, UUID unitId, LocalDate checkIn, LocalDate checkOut,
                        int guestCount, String currency, List<BigDecimal> nightlyRates, BigDecimal fees,
                        BigDecimal taxes, BigDecimal total, String cancellationPolicyVersion,
                        Instant createdAt, Instant expiresAt) {
    public StayQuote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!checkOut.isAfter(checkIn)) throw new IllegalArgumentException("checkOut must be after checkIn");
        if (guestCount < 1) throw new IllegalArgumentException("guestCount must be positive");
        try {
            currency = Currency.getInstance(requireText(currency, "currency")).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency must be a supported ISO-4217 code", exception);
        }
        Objects.requireNonNull(nightlyRates, "nightlyRates");
        nightlyRates = List.copyOf(nightlyRates);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nightlyRates.size() != nights) {
            throw new IllegalArgumentException("nightlyRates must contain exactly one amount per night");
        }
        nightlyRates.forEach(rate -> requireNonNegative(rate, "nightly rate"));
        fees = requireNonNegative(fees, "fees");
        taxes = requireNonNegative(taxes, "taxes");
        Objects.requireNonNull(total, "total");
        BigDecimal calculatedTotal = nightlyRates.stream().reduce(BigDecimal.ZERO, BigDecimal::add).add(fees).add(taxes);
        if (total.compareTo(calculatedTotal) != 0) {
            throw new IllegalArgumentException("total must equal nightly rates plus fees and taxes");
        }
        cancellationPolicyVersion = requireText(cancellationPolicyVersion, "cancellationPolicyVersion");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must be after createdAt");
    }

    public long nights() { return ChronoUnit.DAYS.between(checkIn, checkOut); }

    public boolean isExpiredAt(Instant instant) {
        return !Objects.requireNonNull(instant, "instant").isBefore(expiresAt);
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
