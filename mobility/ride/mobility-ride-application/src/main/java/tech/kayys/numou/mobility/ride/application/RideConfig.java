package tech.kayys.numou.mobility.ride.application;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param searchWindow how long a ride keeps looking for a driver
 * @param offerTtl     how long a driver has to answer an offer
 * @param quoteTtl     how long a quoted price can be booked
 * @param holdFactor   the rider's wallet is reserved for {@code total x holdFactor} (the fare may grow when the trip is metered)
 */
public record RideConfig(Duration searchWindow, Duration offerTtl, Duration quoteTtl, BigDecimal holdFactor) {

    public static RideConfig defaults() {
        return new RideConfig(Duration.ofSeconds(90), Duration.ofSeconds(15), Duration.ofMinutes(5), new BigDecimal("1.2"));
    }
}
