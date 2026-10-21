package tech.kayys.numou.mobility.pricing;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An upfront price shown to the rider and held until {@code expiresAt}. The components always add up:
 * {@code base + distanceFare + timeFare + minimumFareAdjustment + surgeAmount == subtotal} and
 * {@code subtotal - discount + bookingFee == total}.
 *
 * @param surgeAmount extra caused by the surge multiplier and rounding up to the rounding unit
 */
public record FareQuote(UUID id, VehicleType vehicle, double distanceMeters, long durationSeconds, String currency, int fractionDigits,
                        BigDecimal base, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal minimumFareAdjustment,
                        BigDecimal surgeMultiplier, BigDecimal surgeAmount, BigDecimal subtotal, Promo promo, BigDecimal discount,
                        BigDecimal bookingFee, BigDecimal total, Instant createdAt, Instant expiresAt) {

    public Money totalMoney() {
        return new Money(total, tech.kayys.numou.foundation.domain.valueobject.Currency.of(currency));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
