package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.driver.application.DriverView;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Finds a driver for a searching ride.
 *
 * <p><b>Strategy.</b> Offers are sequential (one driver at a time, 15 s to answer): nobody races for a ride, nobody sees an offer
 * that is already gone, and the best-ranked driver is asked first. Candidates come from the location index within a radius that
 * widens as offers go unanswered (2 km, 3.5 km, 5 km). Rank = pickup ETA, adjusted for rating and acceptance rate:
 * {@code eta x (1 + (5 - rating) x 0.05) / max(acceptanceRate, 0.3)}, lower is better. Cash rides skip drivers whose commission
 * debt is at the limit.
 *
 * <p><b>Safety.</b> Accepting is serialised by the ride's version (one live offer, one acceptor) and by the driver's version
 * (one trip at a time). If the driver is claimed but the ride cannot be saved, the claim is undone, except when the ride is in
 * fact assigned to that very driver (a double-tap), in which case it is kept.
 */
public final class DispatchService implements Triggers.Dispatch {

    private static final int MAX_ATTEMPTS = 5;
    private static final int CANDIDATES = 20;
    private static final int EVALUATE_AT_MOST = 8;

    public enum Outcome { OFFERED, OFFER_PENDING, NO_CANDIDATE, NO_DRIVERS, NOT_SEARCHING }

    private record Result(Outcome outcome, Ride ride, UUID offered, RideNotifications.OfferPayload payload, List<UUID> expired) { }

    private record Candidate(DriverView driver, DriverLocationIndex.Nearby nearby, long etaSeconds, double score) { }

    private final RideRepository rides;
    private final DriverService drivers;
    private final DriverLocationIndex index;
    private final RoutingPort routing;
    private final PaymentsPort payments;
    private final RideNotifications notify;
    private final RideWrapUp wrapUp;
    private final RideConfig config;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public DispatchService(RideRepository rides, DriverService drivers, DriverLocationIndex index, RoutingPort routing, PaymentsPort payments,
                           RideNotifications notify, RideWrapUp wrapUp, RideConfig config, UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.rides = rides;
        this.drivers = drivers;
        this.index = index;
        this.routing = routing;
        this.payments = payments;
        this.notify = notify;
        this.wrapUp = wrapUp;
        this.config = config;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Uni<Void> dispatch(UUID tenantId, RideId rideId) {
        return step(tenantId, rideId).replaceWithVoid();
    }

    /** One dispatch step: expire a lapsed offer, then offer to the best available driver, or end the search. */
    public Uni<Outcome> step(UUID tenantId, RideId rideId) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> offerNext(tenantId, ride))))
                .flatMap(res -> afterCommit(tenantId, res).replaceWith(res.outcome()));
    }

    private Uni<Result> offerNext(UUID tenantId, Ride ride) {
        final Instant now = clock.now();
        if (ride.status() != RideStatus.SEARCHING) {
            return Uni.createFrom().item(new Result(Outcome.NOT_SEARCHING, ride, null, null, List.of()));
        }
        final List<UUID> expired = new ArrayList<>();
        ride.expireOffer(now).ifPresent(expired::add);
        if (ride.liveOffer().isPresent()) {
            return Uni.createFrom().item(new Result(Outcome.OFFER_PENDING, ride, null, null, expired));
        }
        if (ride.searchExhausted(now)) {
            return endSearch(tenantId, ride, expired);
        }
        final double radius = ride.offers().size() < 3 ? 2_000 : ride.offers().size() < 6 ? 3_500 : 5_000;
        return index.nearby(tenantId, ride.pickup().point(), radius, ride.vehicle(), now.minusSeconds(30), CANDIDATES).flatMap(nearby ->
                chooseBest(tenantId, ride, nearby, 0, EVALUATE_AT_MOST, new ArrayList<>()).flatMap(best -> {
                    if (best == null) {
                        // nobody suitable right now: keep searching (drivers come online), but persist a lapsed offer
                        return (expired.isEmpty() ? Uni.createFrom().voidItem() : save(tenantId, ride))
                                .replaceWith(new Result(Outcome.NO_CANDIDATE, ride, null, null, expired));
                    }
                    ride.offerTo(best.driver().id(), best.nearby().distanceMeters(), best.etaSeconds(), config.offerTtl(), now);
                    final var payload = new RideNotifications.OfferPayload(ride.id().value(), ride.vehicle().name(), best.nearby().distanceMeters(),
                            best.etaSeconds(), config.offerTtl().getSeconds(), ride.pickup().point().toString(), ride.dropoff().point().toString(),
                            ride.quote().subtotal(), ride.quote().currency(), ride.paymentMethod().name());
                    return save(tenantId, ride).replaceWith(new Result(Outcome.OFFERED, ride, best.driver().id(), payload, expired));
                }));
    }

    /** Evaluates candidates nearest-first (bounded), loading each driver and routing to the pickup, and returns the best-ranked. */
    private Uni<Candidate> chooseBest(UUID tenantId, Ride ride, List<DriverLocationIndex.Nearby> nearby, int i, int budget, List<Candidate> found) {
        if (i >= nearby.size() || budget == 0) {
            return Uni.createFrom().item(found.stream().min(Comparator.comparingDouble(Candidate::score)).orElse(null));
        }
        final var n = nearby.get(i);
        final UUID id = n.fix().driverId().value();
        if (ride.triedDrivers().contains(id)) {
            return chooseBest(tenantId, ride, nearby, i + 1, budget, found);
        }
        return drivers.get(tenantId, id).onFailure(NotFoundException.class).recoverWithNull().flatMap(dv -> {
            if (dv == null || !"ONLINE".equals(dv.status()) || !"APPROVED".equals(dv.verification())) {
                return chooseBest(tenantId, ride, nearby, i + 1, budget, found);
            }
            final Uni<Boolean> cashOk = ride.paymentMethod() == PaymentMethod.CASH ? payments.driverCanAcceptCash(tenantId, id) : Uni.createFrom().item(true);
            return cashOk.flatMap(ok -> {
                if (!ok) return chooseBest(tenantId, ride, nearby, i + 1, budget, found);
                return routing.route(n.fix().at(), ride.pickup().point(), ride.vehicle()).flatMap(route -> {
                    final double score = route.durationSeconds() * (1 + (5 - dv.rating()) * 0.05) / Math.max(dv.acceptanceRate(), 0.3);
                    found.add(new Candidate(dv, n, route.durationSeconds(), score));
                    return chooseBest(tenantId, ride, nearby, i + 1, budget - 1, found);
                });
            });
        });
    }

    private Uni<Result> endSearch(UUID tenantId, Ride ride, List<UUID> expired) {
        ride.markNoDrivers(clock.now());
        return save(tenantId, ride).replaceWith(new Result(Outcome.NO_DRIVERS, ride, null, null, expired));
    }

    private Uni<Void> save(UUID tenantId, Ride ride) {
        return rides.save(tenantId, ride).flatMap(x -> {
            final var raised = ride.pullDomainEvents();
            return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
        });
    }

    /** Side effects that must not run inside the transaction (and must not fail the dispatch). */
    private Uni<Void> afterCommit(UUID tenantId, Result res) {
        Uni<Void> steps = Uni.createFrom().voidItem();
        for (final UUID d : res.expired()) {
            steps = steps.flatMap(x -> quiet(drivers.recordOffer(tenantId, d, false).replaceWithVoid()))
                    .flatMap(x -> quiet(notify.offerWithdrawn(tenantId, d, res.ride().id().value())));
        }
        if (res.outcome() == Outcome.OFFERED) {
            steps = steps.flatMap(x -> quiet(notify.offerToDriver(tenantId, res.offered(), res.payload())));
        } else if (res.outcome() == Outcome.NO_DRIVERS) {
            steps = steps.flatMap(x -> quiet(wrapUp.afterEnd(tenantId, res.ride())));
        }
        return steps;
    }

    // ================================================================ driver responses

    /** The offered driver accepts. Idempotent for a double-tap; refuses late acceptances and drivers who became unavailable. */
    public Uni<RideView> accept(UUID tenantId, UUID driverId, UUID rideUuid) {
        final var rideId = RideId.of(rideUuid);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> {
            if (ride.status() != RideStatus.SEARCHING && ride.driverId().filter(driverId::equals).isPresent()) {
                return Uni.createFrom().item(ride);                                  // already accepted by this driver: same answer again
            }
            ride.acceptOffer(driverId, clock.now());                                // validates and mutates in memory; may throw
            return drivers.claim(tenantId, driverId, rideId)                        // one trip at a time, under the driver's version
                    .flatMap(dv -> save(tenantId, ride))
                    .replaceWith(ride)
                    .onFailure().recoverWithUni(t -> undoClaim(tenantId, driverId, rideId).flatMap(x -> Uni.<Ride>createFrom().failure(t)));
        }))).flatMap(ride -> quiet(drivers.recordOffer(tenantId, driverId, true).replaceWithVoid())
                .flatMap(x -> quiet(notify.rideUpdateToRider(tenantId, ride.riderId().value(), ride.id().value(), ride.status().name())))
                .replaceWith(RideView.forAssignedDriver(ride)));
    }

    /** Frees the driver after a failed accept, unless the ride is actually assigned to them (a concurrent duplicate succeeded). */
    private Uni<Void> undoClaim(UUID tenantId, UUID driverId, RideId rideId) {
        return rides.find(tenantId, rideId).flatMap(o -> o.isPresent() && o.get().driverId().filter(driverId::equals).isPresent()
                ? Uni.createFrom().voidItem()
                : drivers.release(tenantId, driverId, rideId).replaceWithVoid()).onFailure().recoverWithItem((Void) null);
    }

    public Uni<Void> decline(UUID tenantId, UUID driverId, UUID rideUuid) {
        final var rideId = RideId.of(rideUuid);
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> {
            ride.declineOffer(driverId, clock.now());
            return save(tenantId, ride);
        }))).flatMap(x -> quiet(drivers.recordOffer(tenantId, driverId, false).replaceWithVoid()))
                .flatMap(x -> quiet(dispatch(tenantId, rideId)));                    // straight on to the next driver
    }

    // ================================================================ sweeper

    public record SweepResult(int processed, int offered, int noDrivers) { }

    /**
     * Run every few seconds per tenant. Expires lapsed offers, offers to the next driver, and ends searches that ran out of
     * time. This is the safety net behind the event-triggered dispatch: a lost trigger delays a ride by one sweep, never strands it.
     */
    public Uni<SweepResult> sweep(UUID tenantId) {
        return rides.findSearching(tenantId, 200).flatMap(list -> {
            final int[] c = new int[3];
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (final Ride r : list) {
                chain = chain.flatMap(x -> step(tenantId, r.id()).onFailure().recoverWithItem(Outcome.NOT_SEARCHING).invoke(o -> {
                    c[0]++;
                    if (o == Outcome.OFFERED) c[1]++;
                    if (o == Outcome.NO_DRIVERS) c[2]++;
                }).replaceWithVoid());
            }
            return chain.map(x -> new SweepResult(c[0], c[1], c[2]));
        });
    }

    private Uni<Ride> load(UUID tenantId, RideId rideId) {
        return rides.find(tenantId, rideId).map(o -> o.orElseThrow(() -> new NotFoundException("Ride", rideId.value())));
    }

    private static Uni<Void> quiet(Uni<Void> step) {
        return step.onFailure().recoverWithItem((Void) null);
    }
}
