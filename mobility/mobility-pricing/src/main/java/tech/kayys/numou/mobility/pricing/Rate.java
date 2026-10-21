package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;
import java.util.Objects;

/** Tariff of one vehicle class. All amounts are in the schedule's currency. */
public record Rate(BigDecimal base, BigDecimal perKm, BigDecimal perMinute, BigDecimal minimumFare, BigDecimal bookingFee,
                   BigDecimal cancellationFee, BigDecimal noShowFee) {

    public Rate {
        for (final var v : new BigDecimal[]{base, perKm, perMinute, minimumFare, bookingFee, cancellationFee, noShowFee}) {
            Objects.requireNonNull(v, "rate component cannot be null");
            if (v.signum() < 0) {
                throw new IllegalArgumentException("rate components cannot be negative");
            }
        }
    }

    public static Rate of(long base, long perKm, long perMinute, long minimumFare, long bookingFee, long cancellationFee, long noShowFee) {
        return new Rate(BigDecimal.valueOf(base), BigDecimal.valueOf(perKm), BigDecimal.valueOf(perMinute), BigDecimal.valueOf(minimumFare),
                BigDecimal.valueOf(bookingFee), BigDecimal.valueOf(cancellationFee), BigDecimal.valueOf(noShowFee));
    }
}
