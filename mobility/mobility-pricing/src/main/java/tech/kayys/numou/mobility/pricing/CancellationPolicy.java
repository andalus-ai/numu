package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Who pays when a ride is cancelled. Rider cancels: free until a driver is assigned and for a grace window after that,
 * then the cancellation fee. Driver cancels: never costs the rider anything. Rider no-show (driver waited at the pickup):
 * the higher no-show fee. The fee is split between the driver (compensation for the wasted trip) and the platform.
 */
public record CancellationPolicy(Duration freeWindow, Duration noShowWait, BigDecimal driverShare) {

    public static CancellationPolicy defaults() {
        return new CancellationPolicy(Duration.ofMinutes(2), Duration.ofMinutes(5), new BigDecimal("0.80"));
    }

    public record Split(BigDecimal driverPart, BigDecimal platformPart) { }

    /** @param assignedAt null when no driver had accepted yet */
    public BigDecimal riderCancellationFee(Rate rate, Instant assignedAt, Instant now, int fractionDigits) {
        if (assignedAt == null || !now.isAfter(assignedAt.plus(freeWindow))) {
            return BigDecimal.ZERO.setScale(fractionDigits);
        }
        return rate.cancellationFee().setScale(fractionDigits, RoundingMode.HALF_UP);
    }

    public boolean noShowAllowed(Instant arrivedAt, Instant now) {
        return arrivedAt != null && !now.isBefore(arrivedAt.plus(noShowWait));
    }

    public BigDecimal noShowFee(Rate rate, int fractionDigits) {
        return rate.noShowFee().setScale(fractionDigits, RoundingMode.HALF_UP);
    }

    public Split split(BigDecimal fee, int fractionDigits) {
        final BigDecimal driver = fee.multiply(driverShare).setScale(fractionDigits, RoundingMode.HALF_UP);
        return new Split(driver, fee.subtract(driver));
    }
}
