package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.Place;
import tech.kayys.numou.mobility.kernel.geo.ServiceArea;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;
import tech.kayys.numou.mobility.pricing.CancellationPolicy;
import tech.kayys.numou.mobility.pricing.FareCalculator;
import tech.kayys.numou.mobility.pricing.FareSchedule;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.pricing.Promo;
import tech.kayys.numou.mobility.ride.application.RideExceptions.OutsideServiceAreaException;
import tech.kayys.numou.mobility.ride.application.RideExceptions.PinLockedException;
import tech.kayys.numou.mobility.ride.application.RideExceptions.WrongPinException;
import tech.kayys.numou.mobility.ride.domain.ActiveRideExistsException;
import tech.kayys.numou.mobility.ride.domain.ActiveRideLock;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.PinHasher;
import tech.kayys.numou.mobility.ride.spi.ActiveRideLockRepository;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.ride.spi.PromoCatalog;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;
import tech.kayys.numou.mobility.ride.spi.RideRepository;
import tech.kayys.numou.mobility.ride.spi.TripTraceRepository;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.domain.TracePoint;
import tech.kayys.numou.mobility.ride.domain.TripTraceMath;
import tech.kayys.numou.mobility.kernel.geo.PathSimplifier;
import tech.kayys.numou.mobility.kernel.geo.Polyline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rider- and driver-facing ride use cases: quote, book, cancel, arrive, start (PIN), track, complete, rate, tip, SOS.
 *
 * <p>Ownership is enforced here, not only at the HTTP layer: asking for a ride you are not part of is "not found" (never
 * "forbidden"), so ride ids cannot be probed.
 */
public final class RideService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration STALE_LOCK = Duration.ofMinutes(2);

    private final RideRepository rides;
    private final ActiveRideLockRepository locks;
    private final DriverService drivers;
    private final DriverLocationIndex index;
    private final PaymentsPort payments;
    private final RoutingPort routing;
    private final SurgeService surge;
    private final PromoCatalog promos;
    private final QuoteTokens tokens;
    private final RidePins pins;
    private final FareSchedule schedule;
    private final CancellationPolicy cancellation;
    private final List<ServiceArea> areas;
    private final RideConfig config;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final RideNotifications notify;
    private final RideWrapUp wrapUp;
    private final Triggers.Dispatch dispatch;
    private final TripTraceRepository traces;
    private final TripShareTokens shareTokens;

    public RideService(RideRepository rides, ActiveRideLockRepository locks, DriverService drivers, DriverLocationIndex index,
                       PaymentsPort payments, RoutingPort routing, SurgeService surge, PromoCatalog promos, QuoteTokens tokens, RidePins pins,
                       FareSchedule schedule, CancellationPolicy cancellation, List<ServiceArea> areas, RideConfig config, UnitOfWork uow,
                       DomainEventSink events, DomainClock clock, RideNotifications notify, RideWrapUp wrapUp, Triggers.Dispatch dispatch, TripTraceRepository traces, TripShareTokens shareTokens) {
        this.rides = rides;
        this.locks = locks;
        this.drivers = drivers;
        this.index = index;
        this.payments = payments;
        this.routing = routing;
        this.surge = surge;
        this.promos = promos;
        this.tokens = tokens;
        this.pins = pins;
        this.schedule = schedule;
        this.cancellation = cancellation;
        this.areas = areas;
        this.config = config;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.notify = notify;
        this.wrapUp = wrapUp;
        this.dispatch = dispatch;
        this.traces = traces;
        this.shareTokens = shareTokens;
    }

    static String holdRef(RideId id) {
        return "ride:" + id + ":hold";
    }

    // ================================================================ quote

    public Uni<List<QuoteView>> quote(UUID tenantId, UUID riderId, LatLng pickup, LatLng dropoff, Set<VehicleType> types, String promoCode) {
        return quote(tenantId, riderId, pickup, dropoff, "", "", types, promoCode);
    }

    /** @param pickupLabel / dropoffLabel the addresses as the rider chose them; signed into the quote and shown only to the rider and the assigned driver */
    public Uni<List<QuoteView>> quote(UUID tenantId, UUID riderId, LatLng pickup, LatLng dropoff, String pickupLabel, String dropoffLabel,
                                      Set<VehicleType> types, String promoCode) {
        if (!ServiceArea.anyContains(areas, pickup)) {
            return Uni.createFrom().failure(new OutsideServiceAreaException());
        }
        final double straight = pickup.distanceTo(dropoff);
        if (straight < Ride.MIN_TRIP_M || straight > Ride.MAX_TRIP_M) {
            return Uni.createFrom().failure(new BusinessRuleViolation("Choose a destination between 200 m and 150 km away"));
        }
        Uni<List<QuoteView>> acc = Uni.createFrom().item(new ArrayList<QuoteView>());
        for (final VehicleType v : types.stream().sorted().toList()) {
            acc = acc.flatMap(list -> quoteOne(tenantId, riderId, pickup, dropoff, pickupLabel, dropoffLabel, v, promoCode).map(q -> { list.add(q); return list; }));
        }
        return acc;
    }

    private Uni<QuoteView> quoteOne(UUID tenantId, UUID riderId, LatLng pickup, LatLng dropoff, String pickupLabel, String dropoffLabel, VehicleType v, String promoCode) {
        final Instant now = clock.now();
        return routing.route(pickup, dropoff, v).flatMap(route ->
                surge.multiplierFor(tenantId, pickup, v).flatMap(multiplier ->
                        findPromo(tenantId, promoCode, now).flatMap(promo ->
                                pickupEta(tenantId, pickup, v).map(eta -> {
                                    final var q = FareCalculator.quote(schedule, v, Math.max(route.distanceMeters(), 1), route.durationSeconds(), multiplier,
                                            promo.orElse(null), now, config.quoteTtl());
                                    final boolean applied = q.discount().signum() > 0;
                                    final String status = promoCode == null ? "NONE" : promo.isEmpty() ? "INVALID" : applied ? "APPLIED" : "NOT_APPLICABLE";
                                    final var claims = new QuoteTokens.Claims(tenantId, riderId, v, pickup, dropoff, Math.max(route.distanceMeters(), 1),
                                            route.durationSeconds(), multiplier, applied ? promo.get().code() : null, q.total(), q.expiresAt(), pickupLabel, dropoffLabel);
                                    return new QuoteView(tokens.issue(claims), v.name(), q.total(), q.subtotal(), q.bookingFee(), q.discount(), multiplier,
                                            q.distanceMeters(), q.durationSeconds(), eta, q.currency(), q.expiresAt(), status);
                                }))));
    }

    private Uni<Optional<Promo>> findPromo(UUID tenantId, String code, Instant now) {
        return code == null || code.isBlank() ? Uni.createFrom().item(Optional.<Promo>empty()) : promos.find(tenantId, code.trim().toUpperCase(java.util.Locale.ROOT), now);
    }

    /** Seconds for the nearest available driver to reach the pickup, or -1 if none is nearby. */
    private Uni<Long> pickupEta(UUID tenantId, LatLng pickup, VehicleType v) {
        return index.nearby(tenantId, pickup, 3_000, v, clock.now().minusSeconds(30), 1).flatMap(list -> list.isEmpty()
                ? Uni.createFrom().item(-1L)
                : routing.route(list.get(0).fix().at(), pickup, v).map(RoutingPort.RouteEstimate::durationSeconds));
    }

    // ================================================================ booking

    /** Books the quoted ride. Idempotency (a retried tap) is handled by the caller through {@code Idempotency-Key}. */
    public Uni<RideView> request(UUID tenantId, UUID riderUuid, String quoteToken, PaymentMethod method) {
        final Instant requestedAt = clock.now();
        final QuoteTokens.Claims claims = tokens.verify(quoteToken, tenantId, riderUuid, requestedAt);
        if (!ServiceArea.anyContains(areas, claims.pickup())) {
            return Uni.createFrom().failure(new OutsideServiceAreaException());
        }
        final var rideId = RideId.generate();           // fixed across retries: a retry must not create a second ride
        final var riderId = RiderId.of(riderUuid);
        final String pin = pins.pinFor(rideId.value());

        return findPromo(tenantId, claims.promoCode(), requestedAt).flatMap(promo -> {
            if (claims.promoCode() != null && promo.isEmpty()) {
                return Uni.<RideView>createFrom().failure(new BusinessRuleViolation("That promo is no longer valid"));
            }
            return ConflictRetry.run(3, () -> uow.execute(() -> book(tenantId, riderId, rideId, pin, claims, promo.orElse(null), method)))
                    .flatMap(ride -> dispatch.dispatch(tenantId, rideId).onFailure().recoverWithItem((Void) null).replaceWith(ride))
                    .flatMap(ride -> viewForRider(tenantId, ride));
        });
    }

    private Uni<Ride> book(UUID tenantId, RiderId riderId, RideId rideId, String pin, QuoteTokens.Claims claims, Promo promo, PaymentMethod method) {
        final Instant now = clock.now();
        final var quote = FareCalculator.quote(schedule, claims.vehicle(), claims.distanceMeters(), claims.durationSeconds(), claims.surge(), promo, now, config.quoteTtl());
        if (quote.total().compareTo(claims.quotedTotal()) != 0) {
            return Uni.createFrom().failure(new BusinessRuleViolation("The price has changed; please request a new quote"));
        }
        final var ride = Ride.request(rideId, riderId, claims.vehicle(), new Place(claims.pickup(), claims.pickupLabel()), new Place(claims.dropoff(), claims.dropoffLabel()), quote, method,
                PinHasher.hash(rideId.value(), pin), config.searchWindow(), now);
        final String holdRef = holdRef(rideId);
        final BigDecimal hold = quote.total().multiply(config.holdFactor()).setScale(quote.fractionDigits(), RoundingMode.CEILING);

        return redeemPromo(tenantId, riderId, promo, rideId)
                .flatMap(x -> method == PaymentMethod.WALLET ? authorizeWallet(tenantId, riderId, holdRef, hold) : Uni.createFrom().voidItem())
                .flatMap(x -> acquireLock(tenantId, riderId, rideId, now))
                .flatMap(lock -> locks.save(tenantId, lock))
                .flatMap(x -> rides.save(tenantId, ride))
                .flatMap(x -> events.publish(tenantId, ride.pullDomainEvents()))
                .flatMap(x -> surge.recordRequest(tenantId, claims.pickup()))
                .replaceWith(ride)
                .onFailure().recoverWithUni(t -> compensateBooking(tenantId, riderId, rideId, promo, method).flatMap(x -> Uni.<Ride>createFrom().failure(t)));
    }

    private Uni<Void> redeemPromo(UUID tenantId, RiderId riderId, Promo promo, RideId rideId) {
        if (promo == null) return Uni.createFrom().voidItem();
        return promos.redeem(tenantId, riderId.value(), promo.code(), rideId.value()).flatMap(ok -> ok
                ? Uni.createFrom().voidItem()
                : Uni.<Void>createFrom().failure(new BusinessRuleViolation("You have already used that promo")));
    }

    private Uni<Void> authorizeWallet(UUID tenantId, RiderId riderId, String holdRef, BigDecimal hold) {
        return payments.riderCanBook(tenantId, riderId.value()).flatMap(ok -> ok
                ? payments.authorize(tenantId, riderId.value(), holdRef, hold)
                : Uni.<Void>createFrom().failure(new BusinessRuleViolation("Please top up your wallet to clear your outstanding balance")));
    }

    private Uni<ActiveRideLock> acquireLock(UUID tenantId, RiderId riderId, RideId rideId, Instant now) {
        return locks.find(tenantId, riderId).map(o -> o.orElseGet(() -> ActiveRideLock.openFor(riderId, now))).flatMap(lock -> {
            final var holder = lock.holder();
            if (holder.isEmpty() || holder.get().equals(rideId)) {
                lock.acquire(rideId, now);
                return Uni.createFrom().item(lock);
            }
            // Another ride holds it. Only take over if that ride has ended (or never materialised and the lock is stale).
            return rides.find(tenantId, holder.get()).map(r -> {
                final boolean ended = r.isPresent() && !r.get().status().isActive();
                final boolean orphan = r.isEmpty() && Duration.between(lock.getUpdatedAt(), now).compareTo(STALE_LOCK) > 0;
                if (!ended && !orphan) throw new ActiveRideExistsException();
                lock.takeOver(rideId, now);
                return lock;
            });
        });
    }

    private Uni<Void> compensateBooking(UUID tenantId, RiderId riderId, RideId rideId, Promo promo, PaymentMethod method) {
        Uni<Void> steps = Uni.createFrom().voidItem();
        if (method == PaymentMethod.WALLET) {
            steps = steps.flatMap(x -> payments.releaseHold(tenantId, riderId.value(), holdRef(rideId), "ride:" + rideId + ":hold-undo").onFailure().recoverWithItem((Void) null));
        }
        if (promo != null) {
            steps = steps.flatMap(x -> promos.refund(tenantId, riderId.value(), promo.code(), rideId.value()).onFailure().recoverWithItem((Void) null));
        }
        return steps;
    }

    // ================================================================ reads

    public Uni<RideView> get(UUID tenantId, Actor actor, UUID rideId) {
        return load(tenantId, rideId).flatMap(ride -> viewFor(tenantId, actor, ride));
    }

    /** The offer waiting for this driver, if any (coarse places only, until they accept). Expired offers are not returned. */
    public Uni<Optional<RideView>> currentOffer(UUID tenantId, UUID driverId) {
        final Instant now = clock.now();
        return rides.findLiveOfferFor(tenantId, driverId).map(o -> o
                .filter(r -> r.liveOffer().filter(offer -> now.isBefore(offer.expiresAt())).isPresent())
                .map(RideView::forOfferedDriver));
    }

    public Uni<List<RideView>> history(UUID tenantId, UUID riderId, int limit) {
        return rides.history(tenantId, RiderId.of(riderId), Math.min(Math.max(limit, 1), 50)).flatMap(list -> {
            Uni<List<RideView>> acc = Uni.createFrom().item(new ArrayList<RideView>());
            for (final Ride r : list) {
                acc = acc.flatMap(l -> viewForRider(tenantId, r).map(v -> { l.add(v); return l; }));
            }
            return acc;
        });
    }

    private Uni<RideView> viewFor(UUID tenantId, Actor actor, Ride ride) {
        return switch (actor.role()) {
            case RIDER -> ride.riderId().value().equals(actor.id()) ? viewForRider(tenantId, ride) : notFound(ride.id().value());
            case DRIVER -> {
                if (ride.driverId().filter(actor.id()::equals).isPresent()) yield Uni.createFrom().item(RideView.forAssignedDriver(ride));
                if (ride.liveOffer().filter(o -> o.driverId().equals(actor.id())).isPresent()) yield Uni.createFrom().item(RideView.forOfferedDriver(ride));
                yield notFound(ride.id().value());
            }
            case OPS -> viewForRider(tenantId, ride, false);
        };
    }

    private Uni<RideView> viewForRider(UUID tenantId, Ride ride) {
        return viewForRider(tenantId, ride, true);
    }

    private Uni<RideView> viewForRider(UUID tenantId, Ride ride, boolean withPin) {
        final String pin = withPin ? pins.pinFor(ride.id().value()) : null;
        if (ride.driverId().isEmpty()) {
            return Uni.createFrom().item(RideView.forRider(ride, null, null, pin));
        }
        final UUID d = ride.driverId().get();
        return drivers.get(tenantId, d).flatMap(dv -> index.last(tenantId, DriverId.of(d)).map(fix ->
                RideView.forRider(ride, dv, fix.map(DriverLocationIndex.Fix::at).orElse(null), pin)));
    }

    private static <T> Uni<T> notFound(UUID id) {
        return Uni.createFrom().failure(new NotFoundException("Ride", id));
    }

    // ================================================================ rider actions

    public Uni<RideView> cancelByRider(UUID tenantId, UUID riderId, UUID rideId, String reason) {
        return mutate(tenantId, rideId, ride -> {
            requireRider(ride, riderId);
            ride.cancelByRider(reason, cancellation, schedule, clock.now());
        }).flatMap(ride -> wrapUp.afterEnd(tenantId, ride).replaceWith(ride)).flatMap(ride -> viewForRider(tenantId, ride));
    }

    /** Operations intervene (fraud, outage, safety). Free for the rider; the driver is released and the hold returned. */
    public Uni<RideView> cancelBySystem(UUID tenantId, UUID rideId, String reason) {
        return mutate(tenantId, rideId, ride -> ride.cancelBySystem(reason, clock.now()))
                .flatMap(ride -> wrapUp.afterEnd(tenantId, ride).replaceWith(ride)).flatMap(ride -> viewForRider(tenantId, ride, false));
    }

    public Uni<RideView> rateAsRider(UUID tenantId, UUID riderId, UUID rideId, int stars) {
        return mutate(tenantId, rideId, ride -> {
            requireRider(ride, riderId);
            ride.rate(true, stars, clock.now());
        }).flatMap(ride -> {
            final Uni<Void> recordForDriver = ride.driverId()
                    .map(d -> quiet(drivers.recordRating(tenantId, d, stars).replaceWithVoid()))
                    .orElse(Uni.createFrom().voidItem());
            return recordForDriver.replaceWith(ride);
        }).flatMap(ride -> viewForRider(tenantId, ride));
    }

    /** Tips go rider wallet to driver wallet in full (no commission). The payment happens first: if the rider cannot afford it the tip is not recorded. */
    public Uni<RideView> tip(UUID tenantId, UUID riderId, UUID rideId, BigDecimal amount) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> {
            requireRider(ride, riderId);
            ride.addTip(amount, clock.now());                       // validates and mutates in memory only
            final UUID driver = ride.driverId().orElseThrow();
            final String base = "ride:" + rideId + ":tip";
            return payments.debitRiderStrict(tenantId, riderId, ride.tip(), base + ":rider", "tip")
                    .flatMap(x -> payments.moveDriver(tenantId, driver, ride.tip(), base + ":driver", "tip"))
                    .flatMap(x -> rides.save(tenantId, ride))
                    .flatMap(x -> events.publish(tenantId, ride.pullDomainEvents()))
                    .replaceWith(ride);
        }))).flatMap(ride -> viewForRider(tenantId, ride));
    }

    public Uni<RideView> changeDestination(UUID tenantId, UUID riderId, UUID rideId, LatLng newDropoff) {
        return load(tenantId, rideId).flatMap(current -> {
            requireRider(current, riderId);
            return routing.route(current.pickup().point(), newDropoff, current.vehicle()).flatMap(route -> mutate(tenantId, rideId, ride -> {
                final var q = FareCalculator.quote(schedule, ride.vehicle(), Math.max(route.distanceMeters(), 1), route.durationSeconds(),
                        ride.quote().surgeMultiplier(), ride.quote().promo(), clock.now(), config.quoteTtl());
                ride.changeDestination(new Place(newDropoff, ""), q, clock.now());
            })).flatMap(ride -> viewForRider(tenantId, ride));
        });
    }

    public Uni<RideView> sos(UUID tenantId, Actor actor, UUID rideId) {
        return mutate(tenantId, rideId, ride -> {
            requireParticipant(ride, actor);
            ride.triggerSos(actor.role() == Actor.Role.RIDER, clock.now());
        }).flatMap(ride -> viewFor(tenantId, actor, ride));
    }

    // ================================================================ driver actions

    public Uni<RideView> arrive(UUID tenantId, UUID driverId, UUID rideId, LatLng at) {
        return mutate(tenantId, rideId, ride -> {
            requireDriver(ride, driverId);
            ride.markArrived(driverId, at, clock.now());
        }).flatMap(ride -> quiet(notify.rideUpdateToRider(tenantId, ride.riderId().value(), ride.id().value(), ride.status().name())).replaceWith(RideView.forAssignedDriver(ride)));
    }

    /** Starts the trip with the rider's PIN. A wrong PIN is saved (attempt counted) <em>before</em> the error is raised. */
    public Uni<RideView> start(UUID tenantId, UUID driverId, UUID rideId, String pin, LatLng at) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> {
            requireDriver(ride, driverId);
            final var result = ride.start(driverId, pin, at, clock.now());
            return rides.save(tenantId, ride).flatMap(x -> events.publish(tenantId, ride.pullDomainEvents())).map(x -> new Object[]{result, ride});
        }))).flatMap(pair -> {
            final var result = (Ride.StartResult) pair[0];
            final var ride = (Ride) pair[1];
            return switch (result) {
                case STARTED -> traces.append(tenantId, ride.id(), List.of(new TracePoint(at, ride.startedAt())))      // first point of the trace
                        .flatMap(x -> quiet(notify.rideUpdateToRider(tenantId, ride.riderId().value(), ride.id().value(), ride.status().name())))
                        .replaceWith(RideView.forAssignedDriver(ride));
                case WRONG_PIN -> Uni.<RideView>createFrom().failure(new WrongPinException(Ride.MAX_PIN_ATTEMPTS - ride.pinAttempts()));
                case PIN_LOCKED -> Uni.<RideView>createFrom().failure(new PinLockedException());
            };
        });
    }

    public Uni<RideView> complete(UUID tenantId, UUID driverId, UUID rideId) {
        return traces.load(tenantId, RideId.of(rideId)).flatMap(points -> mutate(tenantId, rideId, ride -> {
            requireDriver(ride, driverId);
            if (points.size() >= 2) {
                ride.applyTrackedDistance(TripTraceMath.trackedDistanceMeters(points, ride.vehicle()));   // distance comes from the stored trace
            }
            ride.complete(driverId, schedule, clock.now());
        })).flatMap(ride -> wrapUp.afterEnd(tenantId, ride).replaceWith(RideView.forAssignedDriver(ride)));
    }

    public Uni<RideView> rateAsDriver(UUID tenantId, UUID driverId, UUID rideId, int stars) {
        return mutate(tenantId, rideId, ride -> {
            requireDriver(ride, driverId);
            ride.rate(false, stars, clock.now());
        }).map(RideView::forAssignedDriver);
    }

    /** The driver cannot continue. The rider pays nothing and is matched again (up to twice), and the driver's cancellation is counted. */
    public Uni<RideView> driverCancel(UUID tenantId, UUID driverId, UUID rideId, String reason) {
        final boolean[] searchingAgain = new boolean[1];
        return mutate(tenantId, rideId, ride -> {
            requireDriver(ride, driverId);
            searchingAgain[0] = ride.driverWithdraws(driverId, reason, config.searchWindow(), clock.now());
        }).flatMap(ride -> quiet(drivers.release(tenantId, driverId, ride.id()).replaceWithVoid())
                .flatMap(x -> quiet(drivers.recordCancellation(tenantId, driverId).replaceWithVoid()))
                .flatMap(x -> {
                    if (!searchingAgain[0]) return wrapUp.afterEnd(tenantId, ride);
                    return quiet(notify.rideUpdateToRider(tenantId, ride.riderId().value(), ride.id().value(), "SEARCHING"))
                            .flatMap(y -> quiet(dispatch.dispatch(tenantId, ride.id())));
                }).replaceWith(RideView.forAssignedDriver(ride)));
    }

    public Uni<RideView> noShow(UUID tenantId, UUID driverId, UUID rideId) {
        return mutate(tenantId, rideId, ride -> {
            requireDriver(ride, driverId);
            ride.markNoShow(driverId, cancellation, schedule, clock.now());
        }).flatMap(ride -> wrapUp.afterEnd(tenantId, ride).replaceWith(RideView.forAssignedDriver(ride)));
    }

    /**
     * A GPS fix from a driver. Stores it in the location index; if the driver is on a trip the fix is also appended to the trip's trace
     * (insert-only) and checked against the route corridor. The ride document is rewritten <em>only</em> when the safety state changes
     * (a fix off the corridor, or the return to it): normal driving costs one small insert, not a document update.
     */
    public Uni<DriverService.LocationOutcome> trackPosition(UUID tenantId, UUID driverId, LatLng at, double heading, double speedKmh, Instant recordedAt) {
        return drivers.updateLocation(tenantId, driverId, at, heading, speedKmh, recordedAt).flatMap(res -> {
            if (res.outcome() != DriverService.LocationOutcome.STORED || res.activeRideId() == null) {
                return Uni.createFrom().item(res.outcome());
            }
            final var rideId = RideId.of(res.activeRideId());
            return rides.find(tenantId, rideId).flatMap(found -> {
                if (found.isEmpty() || found.get().status() != RideStatus.IN_PROGRESS) {
                    return Uni.createFrom().item(res.outcome());
                }
                return traces.append(tenantId, rideId, List.of(new TracePoint(at, recordedAt))).flatMap(x -> {
                    if (!found.get().observeDeviation(at, clock.now())) {
                        return Uni.createFrom().item(res.outcome());                // on route: nothing more to do
                    }
                    return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, res.activeRideId()).flatMap(ride -> {
                        if (!ride.observeDeviation(at, clock.now())) return Uni.createFrom().voidItem();
                        return rides.save(tenantId, ride).flatMap(y -> {
                            final var raised = ride.pullDomainEvents();
                            return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
                        });
                    }))).replaceWith(res.outcome());
                });
            });
        });
    }

    // ================================================================ evidence and sharing

    /** The route of a trip for its rider, its driver or operations. Simplified to 10 m: a few hundred bytes instead of thousands of fixes. */
    public Uni<TraceView> trace(UUID tenantId, Actor actor, UUID rideId) {
        return load(tenantId, rideId).flatMap(ride -> {
            final boolean allowed = actor.role() == Actor.Role.OPS
                    || (actor.role() == Actor.Role.RIDER && ride.riderId().value().equals(actor.id()))
                    || (actor.role() == Actor.Role.DRIVER && ride.driverId().filter(actor.id()::equals).isPresent());
            if (!allowed) return notFound(rideId);
            return traces.load(tenantId, ride.id()).map(points -> {
                final var path = TripTraceMath.path(points);
                final var simple = PathSimplifier.simplify(path, 10);
                return new TraceView(Polyline.encode(simple), points.size(), simple.size(), TripTraceMath.trackedDistanceMeters(points, ride.vehicle()));
            });
        });
    }

    /** A link the rider can send to family. Valid while the trip is active, for at most 6 hours. */
    public Uni<String> shareLink(UUID tenantId, UUID riderId, UUID rideId, Duration validFor) {
        return load(tenantId, rideId).map(ride -> {
            requireRider(ride, riderId);
            if (ride.status() != RideStatus.DRIVER_ASSIGNED && ride.status() != RideStatus.DRIVER_ARRIVED && ride.status() != RideStatus.IN_PROGRESS) {
                throw new BusinessRuleViolation("A ride can only be shared while a driver is assigned or the trip is under way");
            }
            final Duration ttl = validFor == null || validFor.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : validFor;
            return shareTokens.issue(new TripShareTokens.Claims(tenantId, ride.id().value(), clock.now().plus(ttl)));
        });
    }

    /** The unauthenticated view behind a share link. Ended trips show only their final status, for one hour. */
    public Uni<PublicTrackingView> publicTracking(String token) {
        final var claims = shareTokens.verify(token, clock.now());
        return load(claims.tenantId(), claims.rideId()).flatMap(ride -> {
            final boolean active = ride.status().isActive();
            final Instant endedAt = ride.completedAt() != null ? ride.completedAt() : null;
            if (!active && (endedAt == null || clock.now().isAfter(endedAt.plus(Duration.ofHours(1))))) {
                return Uni.<PublicTrackingView>createFrom().failure(new BusinessRuleViolation("This link is invalid or has expired"));
            }
            if (ride.driverId().isEmpty()) {
                return Uni.createFrom().item(new PublicTrackingView(ride.status().name(), !active, null, ride.vehicle().name(), null, null, null, null, null, clock.now()));
            }
            final UUID d = ride.driverId().get();
            return drivers.get(claims.tenantId(), d).flatMap(dv -> (active ? index.last(claims.tenantId(), DriverId.of(d)) : Uni.createFrom().item(Optional.<DriverLocationIndex.Fix>empty()))
                    .map(fix -> new PublicTrackingView(ride.status().name(), !active, dv.name().split("\\s+")[0], dv.vehicleType(), dv.plate(), dv.model(), dv.color(),
                            fix.map(f -> f.at().lat()).orElse(null), fix.map(f -> f.at().lng()).orElse(null), clock.now())));
        });
    }

    // ================================================================ internals

    private Uni<Ride> mutate(UUID tenantId, UUID rideId, Consumer<Ride> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, rideId).flatMap(ride -> {
            action.accept(ride);
            return rides.save(tenantId, ride).flatMap(x -> {
                final var raised = ride.pullDomainEvents();
                return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
            }).replaceWith(ride);
        })));
    }

    private Uni<Ride> load(UUID tenantId, UUID rideId) {
        return rides.find(tenantId, RideId.of(rideId)).map(o -> o.orElseThrow(() -> new NotFoundException("Ride", rideId)));
    }

    private static void requireRider(Ride ride, UUID riderId) {
        if (!ride.riderId().value().equals(riderId)) throw new NotFoundException("Ride", ride.id().value());
    }

    private static void requireDriver(Ride ride, UUID driverId) {
        if (ride.driverId().filter(driverId::equals).isEmpty()) throw new NotFoundException("Ride", ride.id().value());
    }

    private static void requireParticipant(Ride ride, Actor actor) {
        final boolean ok = actor.role() == Actor.Role.RIDER ? ride.riderId().value().equals(actor.id())
                : actor.role() == Actor.Role.DRIVER && ride.driverId().filter(actor.id()::equals).isPresent();
        if (!ok) throw new NotFoundException("Ride", ride.id().value());
    }

    private static Uni<Void> quiet(Uni<Void> step) {
        return step.onFailure().recoverWithItem((Void) null);
    }
}
