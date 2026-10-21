package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.pricing.CancellationPolicy;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.pricing.Settlement;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Moves the money of an ended ride exactly once.
 *
 * <p>Every wallet operation carries a reference derived from the ride id, so running settlement twice (a redelivered event,
 * the sweeper racing the inline call) changes nothing the second time. The ride's {@code settled} flag is written last; if the
 * process dies before it, the next run repeats idempotent operations and then sets it.
 *
 * <ul>
 *   <li>COMPLETED: rider charged {@code F - D + B}, driver credited, platform credited (see {@link Settlement}); cash rides debit the
 *       driver their commission instead of charging the rider.</li>
 *   <li>CANCELLED with a fee: rider charged the fee (consuming the hold), shared driver/platform; without a fee the hold is released.</li>
 *   <li>NO_DRIVERS: the hold is released.</li>
 * </ul>
 */
public final class RideSettlementService implements Triggers.Settle {

    private static final int MAX_ATTEMPTS = 5;

    private final RideRepository rides;
    private final PaymentsPort payments;
    private final BigDecimal commissionRate;
    private final CancellationPolicy cancellation;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public RideSettlementService(RideRepository rides, PaymentsPort payments, BigDecimal commissionRate, CancellationPolicy cancellation,
                                 UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.rides = rides;
        this.payments = payments;
        this.commissionRate = commissionRate;
        this.cancellation = cancellation;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Uni<Void> settle(UUID tenantId, RideId rideId) {
        return rides.find(tenantId, rideId).map(o -> o.orElseThrow(() -> new NotFoundException("Ride", rideId.value()))).flatMap(ride -> {
            if (ride.settled()) return Uni.createFrom().voidItem();
            return switch (ride.status()) {
                case COMPLETED -> settleCompleted(tenantId, ride);
                case CANCELLED -> settleCancelled(tenantId, ride);
                case NO_DRIVERS -> releaseHold(tenantId, ride);
                default -> Uni.createFrom().voidItem();           // still active: nothing to settle yet
            };
        });
    }

    private Uni<Void> settleCompleted(UUID tenantId, Ride ride) {
        final var fare = ride.finalFare();
        final int digits = ride.quote().fractionDigits();
        final var s = Settlement.compute(fare.subtotal(), fare.discount(), fare.bookingFee(), BigDecimal.ZERO.setScale(digits), commissionRate, ride.paymentMethod(), digits);
        final String base = "ride:" + ride.id() + ":settle";
        final UUID rider = ride.riderId().value();
        final UUID driver = ride.driverId().orElseThrow();

        Uni<Void> steps = Uni.createFrom().voidItem();
        if (ride.paymentMethod() == PaymentMethod.WALLET) {
            steps = steps.flatMap(x -> payments.chargeRider(tenantId, rider, RideService.holdRef(ride.id()), s.riderPays(), base + ":rider", "ride"));
        }
        steps = steps.flatMap(x -> s.driverWalletMove().signum() == 0 ? Uni.createFrom().voidItem()
                        : payments.moveDriver(tenantId, driver, s.driverWalletMove(), base + ":driver", "ride"))
                .flatMap(x -> s.platformWalletMove().signum() == 0 ? Uni.createFrom().voidItem()
                        : payments.movePlatform(tenantId, ride.id().value(), s.platformWalletMove(), base + ":platform", "ride"));
        return steps.flatMap(x -> markSettled(tenantId, ride.id()));
    }

    private Uni<Void> settleCancelled(UUID tenantId, Ride ride) {
        final BigDecimal fee = ride.cancelFee() == null ? BigDecimal.ZERO : ride.cancelFee();
        if (fee.signum() == 0) return releaseHold(tenantId, ride);
        final int digits = ride.quote().fractionDigits();
        final var split = ride.driverId().isPresent() ? cancellation.split(fee, digits)
                : new CancellationPolicy.Split(BigDecimal.ZERO.setScale(digits), fee);          // no driver was involved: all platform
        final var s = Settlement.forFee(fee, split, digits);
        final String base = "ride:" + ride.id() + ":fee";
        Uni<Void> steps = payments.chargeRider(tenantId, ride.riderId().value(), RideService.holdRef(ride.id()), fee, base + ":rider", "cancellation fee");
        if (s.driverWalletMove().signum() > 0 && ride.driverId().isPresent()) {
            steps = steps.flatMap(x -> payments.moveDriver(tenantId, ride.driverId().get(), s.driverWalletMove(), base + ":driver", "cancellation fee"));
        }
        if (s.platformWalletMove().signum() > 0) {
            steps = steps.flatMap(x -> payments.movePlatform(tenantId, ride.id().value(), s.platformWalletMove(), base + ":platform", "cancellation fee"));
        }
        return steps.flatMap(x -> markSettled(tenantId, ride.id()));
    }

    private Uni<Void> releaseHold(UUID tenantId, Ride ride) {
        if (ride.paymentMethod() != PaymentMethod.WALLET) return Uni.createFrom().voidItem();
        return payments.releaseHold(tenantId, ride.riderId().value(), RideService.holdRef(ride.id()), "ride:" + ride.id() + ":hold-release");
    }

    private Uni<Void> markSettled(UUID tenantId, RideId rideId) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> rides.find(tenantId, rideId).map(o -> o.orElseThrow()).flatMap(ride -> {
            if (!ride.markSettled(clock.now())) return Uni.createFrom().voidItem();
            return rides.save(tenantId, ride);
        })));
    }

    /** Safety net: settle every ended ride that is not marked settled (a crashed or lagging event consumer). */
    public Uni<Integer> sweepUnsettled(UUID tenantId) {
        return rides.findEndedUnsettled(tenantId, 200).flatMap(list -> {
            final int[] done = {0};
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (final Ride r : list) {
                chain = chain.flatMap(x -> settle(tenantId, r.id()).invoke(() -> done[0]++).onFailure().recoverWithItem((Void) null));
            }
            return chain.map(x -> done[0]);
        });
    }
}
