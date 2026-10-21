package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.pricing.Promo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Promo codes and their per-rider redemption (a code may be limited to one use per rider). */
public interface PromoCatalog {

    Uni<Optional<Promo>> find(UUID tenantId, String code, Instant now);

    /** Reserves the redemption for this ride. @return false if the rider already used the code on a different ride. */
    Uni<Boolean> redeem(UUID tenantId, UUID riderId, String code, UUID rideId);

    /** Gives the redemption back (ride cancelled / no drivers). Safe to call twice. */
    Uni<Void> refund(UUID tenantId, UUID riderId, String code, UUID rideId);
}
