package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How the ride context moves money, expressed in ride terms. Every call carries a {@code ref} that makes it idempotent:
 * settlement can be retried safely after a crash or a redelivered event. Implemented over the wallet context.
 */
public interface PaymentsPort {

    /** Reserves the amount on the rider's wallet. Fails if the balance is insufficient. */
    Uni<Void> authorize(UUID tenantId, UUID riderId, String holdRef, BigDecimal amount);

    Uni<Void> releaseHold(UUID tenantId, UUID riderId, String holdRef, String ref);

    Uni<Boolean> riderCanBook(UUID tenantId, UUID riderId);

    Uni<Boolean> driverCanAcceptCash(UUID tenantId, UUID driverId);

    /** Charges the rider (consuming the hold if {@code holdRef} is given). Never refuses: the service was delivered; a shortfall becomes debt. */
    Uni<Void> chargeRider(UUID tenantId, UUID riderId, String holdRef, BigDecimal amount, String ref, String memo);

    /** Credits the rider's wallet (refunds, goodwill). Idempotent per {@code ref}. */
    Uni<Void> creditRider(UUID tenantId, UUID riderId, BigDecimal amount, String ref, String memo);

    /** Strict debit for voluntary payments (tips): refused if the rider cannot afford it. */
    Uni<Void> debitRiderStrict(UUID tenantId, UUID riderId, BigDecimal amount, String ref, String memo);

    /** Positive credits the driver; negative charges them (cash commission owed) and never refuses. */
    Uni<Void> moveDriver(UUID tenantId, UUID driverId, BigDecimal signedAmount, String ref, String memo);

    /** Platform revenue (positive) or promo subsidy (negative), spread over shards by {@code shardKey}. */
    Uni<Void> movePlatform(UUID tenantId, UUID shardKey, BigDecimal signedAmount, String ref, String memo);
}
