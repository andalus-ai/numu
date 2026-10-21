package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.MovementRules;
import tech.kayys.numou.mobility.kernel.geo.Place;
import tech.kayys.numou.mobility.pricing.CancellationPolicy;
import tech.kayys.numou.mobility.pricing.FareCalculator;
import tech.kayys.numou.mobility.pricing.FareQuote;
import tech.kayys.numou.mobility.pricing.FareSchedule;
import tech.kayys.numou.mobility.pricing.FinalFare;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.domain.event.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One trip, from request to rating.
 *
 * <pre>
 *   SEARCHING -> DRIVER_ASSIGNED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
 *        |              |                 |
 *        +-> NO_DRIVERS +-> (driver withdraws: back to SEARCHING, max 2x, else CANCELLED)
 *        +-----------------+-> CANCELLED (rider / no-show / system)
 * </pre>
 *
 * <p>Consistency rules enforced here: one live offer at a time; only that driver can accept it, and only before it expires;
 * a driver is never re-offered a ride they already saw; arrival and start are geofenced; the trip starts only with the
 * rider's PIN (5 tries); distance is accumulated only from plausible GPS fixes; the final fare follows the upfront/metered
 * rules of {@link FareCalculator}.
 *
 * <p>Domain events carry ids, enums and amounts, plus a coarse ~1 km pickup cell: never addresses or exact coordinates.
 */
public final class Ride extends VersionedAggregate<RideId> {

    private static final long serialVersionUID = 1L;
    public static final int MAX_OFFERS = 12;
    public static final int MAX_REASSIGNMENTS = 2;
    public static final int MAX_PIN_ATTEMPTS = 5;
    public static final double ARRIVE_RADIUS_M = 150.0;
    public static final double START_RADIUS_M = 300.0;
    public static final double MIN_TRIP_M = 200.0;
    public static final double MAX_TRIP_M = 150_000.0;
    public static final Duration RATING_WINDOW = Duration.ofHours(24);

    public enum StartResult { STARTED, WRONG_PIN, PIN_LOCKED }

    public record Snapshot(UUID id, UUID riderId, String vehicle, Place pickup, Place dropoff, FareQuote quote, String paymentMethod,
                           String pinHash, int pinAttempts, String status, Instant requestedAt, Instant searchDeadline, List<Offer> offers,
                           UUID driverId, Instant assignedAt, Instant arrivedAt, Instant startedAt, Instant completedAt, double trackedMeters,
                           LatLng lastPoint, Instant lastPointAt, FinalFare finalFare, BigDecimal tip, String cancelledBy,
                           String cancelReason, BigDecimal cancelFee, Instant cancelledAt, int reassignments, int riderRatingOfDriver,
                           int driverRatingOfRider, boolean settled, Instant sosAt, int offRouteStreak, Instant deviationFlaggedAt,
                           BigDecimal refunded, List<String> refundRefs, Instant createdAt, Instant updatedAt, int version) { }

    private final RiderId riderId;
    private final VehicleType vehicle;
    private final Place pickup;
    private Place dropoff;
    private FareQuote quote;
    private final PaymentMethod paymentMethod;
    private final String pinHash;
    private int pinAttempts;
    private RideStatus status = RideStatus.SEARCHING;
    private final Instant requestedAt;
    private Instant searchDeadline;
    private final List<Offer> offers = new ArrayList<>();
    private UUID driverId;
    private Instant assignedAt;
    private Instant arrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private double trackedMeters;
    private LatLng lastPoint;
    private Instant lastPointAt;
    private FinalFare finalFare;
    private BigDecimal tip;
    private CancelledBy cancelledBy;
    private String cancelReason;
    private BigDecimal cancelFee;
    private Instant cancelledAt;
    private int reassignments;
    private int riderRatingOfDriver;
    private int driverRatingOfRider;
    private boolean settled;
    private Instant sosAt;
    private int offRouteStreak;
    private Instant deviationFlaggedAt;
    private BigDecimal refunded = BigDecimal.ZERO;
    private final List<String> refundRefs = new ArrayList<>();

    private Ride(RideId id, RiderId riderId, VehicleType vehicle, Place pickup, Place dropoff, FareQuote quote, PaymentMethod method,
                 String pinHash, Instant requestedAt, Instant searchDeadline) {
        super(id, requestedAt);
        this.riderId = Objects.requireNonNull(riderId, "riderId cannot be null");
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle cannot be null");
        this.pickup = Objects.requireNonNull(pickup, "pickup cannot be null");
        this.dropoff = Objects.requireNonNull(dropoff, "dropoff cannot be null");
        this.quote = Objects.requireNonNull(quote, "quote cannot be null");
        this.paymentMethod = Objects.requireNonNull(method, "paymentMethod cannot be null");
        this.pinHash = Objects.requireNonNull(pinHash, "pinHash cannot be null");
        this.requestedAt = requestedAt;
        this.searchDeadline = searchDeadline;
    }

    public static Ride request(RideId id, RiderId riderId, VehicleType vehicle, Place pickup, Place dropoff, FareQuote quote,
                               PaymentMethod method, String pinHash, Duration searchWindow, Instant now) {
        final double straight = pickup.point().distanceTo(dropoff.point());
        if (straight < MIN_TRIP_M) {
            throw new BusinessRuleViolation("Pickup and drop-off are too close together");
        }
        if (straight > MAX_TRIP_M) {
            throw new BusinessRuleViolation("Trip is too long");
        }
        requireUsableQuote(quote, vehicle, now);
        final var r = new Ride(id, riderId, vehicle, pickup, dropoff, quote, method, pinHash, now, now.plus(searchWindow));
        r.raise(RideRequested.at(now, id.value(), riderId.value(), vehicle.name(), GeoCell.of(pickup.point()).key(), quote.currency(), quote.total()));
        return r;
    }

    // ---------------------------------------------------------------- matching

    /** Offers the ride to {@code driverId}. Never to someone who already saw it, and never while another offer is live. */
    public void offerTo(UUID driverId, double distanceMeters, long etaSeconds, Duration ttl, Instant now) {
        requireStatus(RideStatus.SEARCHING, "offer");
        if (liveOffer().isPresent()) {
            throw new InvalidStateException("An offer is already pending");
        }
        if (!now.isBefore(searchDeadline)) {
            throw new BusinessRuleViolation("Search window has closed");
        }
        if (offers.size() >= MAX_OFFERS) {
            throw new BusinessRuleViolation("Offer limit reached");
        }
        if (triedDrivers().contains(driverId)) {
            throw new BusinessRuleViolation("Driver already saw this ride");
        }
        offers.add(new Offer(driverId, now, now.plus(ttl), Offer.Status.OFFERED, distanceMeters, etaSeconds));
        touch(now);
        raise(RideOffered.at(now, id().value(), driverId, offers.size()));
    }

    /** Marks the live offer expired if its time is up. @return the driver whose offer lapsed, if any. */
    public Optional<UUID> expireOffer(Instant now) {
        final var live = liveOffer();
        if (status != RideStatus.SEARCHING || live.isEmpty() || now.isBefore(live.get().expiresAt())) {
            return Optional.empty();
        }
        replace(live.get(), live.get().with(Offer.Status.EXPIRED));
        touch(now);
        raise(RideOfferExpired.at(now, id().value(), live.get().driverId()));
        return Optional.of(live.get().driverId());
    }

    public void declineOffer(UUID driverId, Instant now) {
        final var live = requireLiveOfferFor(driverId);
        replace(live, live.with(Offer.Status.DECLINED));
        touch(now);
        raise(RideOfferDeclined.at(now, id().value(), driverId));
    }

    /** Accepts the live offer. Throws {@link OfferExpiredException} if it lapsed or was never made to this driver. */
    public void acceptOffer(UUID driverId, Instant now) {
        final var live = requireLiveOfferFor(driverId);
        if (!now.isBefore(live.expiresAt())) {
            throw new OfferExpiredException("The offer has expired");
        }
        replace(live, live.with(Offer.Status.ACCEPTED));
        this.driverId = driverId;
        this.assignedAt = now;
        this.status = RideStatus.DRIVER_ASSIGNED;
        touch(now);
        raise(RideAssigned.at(now, id().value(), riderId.value(), driverId, live.etaSeconds()));
    }

    public void markNoDrivers(Instant now) {
        requireStatus(RideStatus.SEARCHING, "end the search");
        status = RideStatus.NO_DRIVERS;
        touch(now);
        raise(RideNoDrivers.at(now, id().value(), riderId.value()));
    }

    /** Searching and nobody is currently being asked: time to pick the next driver. */
    public boolean needsOffer(Instant now) {
        return status == RideStatus.SEARCHING && liveOffer().isEmpty() && now.isBefore(searchDeadline) && offers.size() < MAX_OFFERS;
    }

    public boolean searchExhausted(Instant now) {
        return status == RideStatus.SEARCHING && liveOffer().isEmpty() && (!now.isBefore(searchDeadline) || offers.size() >= MAX_OFFERS);
    }

    public Set<UUID> triedDrivers() {
        return offers.stream().map(Offer::driverId).collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- pickup

    public void markArrived(UUID byDriver, LatLng driverAt, Instant now) {
        requireStatus(RideStatus.DRIVER_ASSIGNED, "arrive");
        requireDriver(byDriver);
        if (driverAt.distanceTo(pickup.point()) > ARRIVE_RADIUS_M) {
            throw new BusinessRuleViolation("You are not at the pickup point yet");
        }
        arrivedAt = now;
        status = RideStatus.DRIVER_ARRIVED;
        touch(now);
        raise(DriverArrived.at(now, id().value(), riderId.value(), byDriver));
    }

    /**
     * Starts the trip if the PIN is right. Returns a result instead of throwing on a wrong PIN: the failed attempt must be
     * <em>persisted</em> for the 5-try lockout to mean anything, and an exception would roll the counter back.
     */
    public StartResult start(UUID byDriver, String pin, LatLng driverAt, Instant now) {
        requireStatus(RideStatus.DRIVER_ARRIVED, "start");
        requireDriver(byDriver);
        if (driverAt.distanceTo(pickup.point()) > START_RADIUS_M) {
            throw new BusinessRuleViolation("You are not at the pickup point");
        }
        if (pinAttempts >= MAX_PIN_ATTEMPTS) {
            return StartResult.PIN_LOCKED;
        }
        if (!PinHasher.matches(id().value(), pin, pinHash)) {
            pinAttempts++;
            touch(now);
            return pinAttempts >= MAX_PIN_ATTEMPTS ? StartResult.PIN_LOCKED : StartResult.WRONG_PIN;
        }
        startedAt = now;
        lastPoint = driverAt;
        lastPointAt = now;
        status = RideStatus.IN_PROGRESS;
        touch(now);
        raise(RideStarted.at(now, id().value(), riderId.value(), byDriver));
        return StartResult.STARTED;
    }

    // ---------------------------------------------------------------- trip

    /** Adds a GPS fix to the trip's tracked distance. Implausible or out-of-order fixes are ignored. */
    public boolean recordPosition(LatLng point, Instant at) {
        if (status != RideStatus.IN_PROGRESS) return false;
        if (!MovementRules.isPlausible(lastPoint, lastPointAt, point, at, vehicle)) return false;
        final double d = lastPoint.distanceTo(point);
        if (d >= MovementRules.JITTER_METERS) {
            trackedMeters += d;
            lastPoint = point;
            lastPointAt = at;
        }
        return true;
    }

    /** Sets the distance computed from the stored trace, just before completion. Never decreases what live tracking already counted. */
    public void applyTrackedDistance(double meters) {
        requireStatus(RideStatus.IN_PROGRESS, "set the distance of");
        if (meters > trackedMeters) {
            trackedMeters = meters;
        }
    }

    /**
     * Safety check: has the vehicle left the corridor between pickup and drop-off for three consecutive fixes? Raises one
     * {@code route-deviated} event per ride so operations can look (a detour for fuel or a road closure is normal; a long way off is not).
     *
     * @return true if the ride changed and must be saved (streak moved or the event was raised); false for the common on-route case
     */
    public boolean observeDeviation(LatLng at, Instant now) {
        if (status != RideStatus.IN_PROGRESS) return false;
        final double straight = pickup.point().distanceTo(dropoff.point());
        final double threshold = Math.max(1_500.0, 0.5 * straight);
        final double off = tech.kayys.numou.mobility.kernel.geo.GeoMath.distanceToSegmentMeters(at, pickup.point(), dropoff.point());
        if (off > threshold) {
            offRouteStreak++;
            if (offRouteStreak >= 3 && deviationFlaggedAt == null) {
                deviationFlaggedAt = now;
                raise(RideRouteDeviated.at(now, id().value(), riderId.value(), driverId, Math.round(off)));
            }
            touch(now);
            return true;
        }
        if (offRouteStreak > 0) {
            offRouteStreak = 0;
            touch(now);
            return true;
        }
        return false;
    }

    /** New destination mid-trip (or before pickup) with a fresh quote at the surge locked at booking. */
    public void changeDestination(Place newDropoff, FareQuote newQuote, Instant now) {
        if (status != RideStatus.DRIVER_ASSIGNED && status != RideStatus.DRIVER_ARRIVED && status != RideStatus.IN_PROGRESS) {
            throw new InvalidStateException("The destination can only change once a driver is assigned");
        }
        if (newQuote.surgeMultiplier().compareTo(quote.surgeMultiplier()) != 0) {
            throw new BusinessRuleViolation("Surge is locked at booking time");
        }
        final double straight = pickup.point().distanceTo(newDropoff.point());
        if (straight < MIN_TRIP_M || straight > MAX_TRIP_M) {
            throw new BusinessRuleViolation("Invalid destination");
        }
        requireUsableQuote(newQuote, vehicle, now);
        dropoff = newDropoff;
        quote = newQuote;
        touch(now);
        raise(RideDestinationChanged.at(now, id().value(), newQuote.total()));
    }

    public FinalFare complete(UUID byDriver, FareSchedule schedule, Instant now) {
        requireStatus(RideStatus.IN_PROGRESS, "complete");
        requireDriver(byDriver);
        final long seconds = Math.max(0, Duration.between(startedAt, now).getSeconds());
        final double straight = pickup.point().distanceTo(dropoff.point());
        // GPS gaps (tunnels, dead zones) make the tracked distance too short to trust: then the upfront price stands.
        final boolean trackingReliable = trackedMeters >= 0.5 * straight;
        finalFare = trackingReliable
                ? FareCalculator.finalFare(quote, schedule, trackedMeters, seconds, now)
                : new FinalFare(FinalFare.Basis.UPFRONT, quote.subtotal(), quote.discount(), quote.bookingFee(), quote.total());
        completedAt = now;
        status = RideStatus.COMPLETED;
        touch(now);
        raise(RideCompleted.at(now, id().value(), riderId.value(), byDriver, vehicle.name(), paymentMethod.name(), quote.currency(),
                finalFare.subtotal(), finalFare.discount(), finalFare.bookingFee(), finalFare.total(), trackedMeters, seconds, finalFare.basis().name()));
        return finalFare;
    }

    // ---------------------------------------------------------------- cancellation

    /** Rider cancels. Fee depends on whether and how long ago a driver was assigned. */
    public BigDecimal cancelByRider(String reason, CancellationPolicy policy, FareSchedule schedule, Instant now) {
        if (status != RideStatus.SEARCHING && status != RideStatus.DRIVER_ASSIGNED && status != RideStatus.DRIVER_ARRIVED) {
            throw new InvalidStateException("A ride in state " + status + " cannot be cancelled by the rider");
        }
        final BigDecimal fee = policy.riderCancellationFee(schedule.rate(vehicle), driverId == null ? null : assignedAt, now, quote.fractionDigits());
        finishCancelled(CancelledBy.RIDER, reason, fee, now);
        return fee;
    }

    /** The driver waited at the pickup and the rider never came. */
    public BigDecimal markNoShow(UUID byDriver, CancellationPolicy policy, FareSchedule schedule, Instant now) {
        requireStatus(RideStatus.DRIVER_ARRIVED, "mark no-show");
        requireDriver(byDriver);
        if (!policy.noShowAllowed(arrivedAt, now)) {
            throw new BusinessRuleViolation("Please wait a little longer for the rider");
        }
        final BigDecimal fee = policy.noShowFee(schedule.rate(vehicle), quote.fractionDigits());
        finishCancelled(CancelledBy.RIDER_NO_SHOW, "rider did not show", fee, now);
        return fee;
    }

    /**
     * The assigned driver cancels. The rider pays nothing and is matched again; the driver is remembered as having seen
     * the ride. After {@value #MAX_REASSIGNMENTS} reassignments the ride is cancelled by the system instead.
     *
     * @return true if the ride is searching again, false if it was cancelled
     */
    public boolean driverWithdraws(UUID byDriver, String reason, Duration searchWindow, Instant now) {
        if (status != RideStatus.DRIVER_ASSIGNED && status != RideStatus.DRIVER_ARRIVED) {
            throw new InvalidStateException("The driver cannot withdraw from a ride in state " + status);
        }
        requireDriver(byDriver);
        raise(RideDriverWithdrew.at(now, id().value(), byDriver, reassignments + 1));
        if (reassignments >= MAX_REASSIGNMENTS) {
            finishCancelled(CancelledBy.SYSTEM, "no replacement driver", BigDecimal.ZERO.setScale(quote.fractionDigits()), now);
            return false;
        }
        reassignments++;
        driverId = null;
        assignedAt = null;
        arrivedAt = null;
        pinAttempts = 0;
        status = RideStatus.SEARCHING;
        searchDeadline = now.plus(searchWindow);
        touch(now);
        return true;
    }

    public void cancelBySystem(String reason, Instant now) {
        if (!status.isActive()) {
            throw new InvalidStateException("Ride is already " + status);
        }
        finishCancelled(CancelledBy.SYSTEM, reason, BigDecimal.ZERO.setScale(quote.fractionDigits()), now);
    }

    private void finishCancelled(CancelledBy by, String reason, BigDecimal fee, Instant now) {
        final UUID hadDriver = driverId;
        cancelledBy = by;
        cancelReason = reason == null ? "" : reason;
        cancelFee = fee;
        cancelledAt = now;
        status = RideStatus.CANCELLED;
        touch(now);
        raise(RideCancelled.at(now, id().value(), riderId.value(), hadDriver, by.name(), fee, quote.currency(), hadDriver != null));
    }

    // ---------------------------------------------------------------- after the trip

    public void rate(boolean byRider, int stars, Instant now) {
        if (status != RideStatus.COMPLETED || now.isAfter(completedAt.plus(RATING_WINDOW))) {
            throw new BusinessRuleViolation("This ride can no longer be rated");
        }
        if (stars < 1 || stars > 5) {
            throw new BusinessRuleViolation("Rating must be 1-5");
        }
        if (byRider ? riderRatingOfDriver != 0 : driverRatingOfRider != 0) {
            throw new BusinessRuleViolation("Already rated");
        }
        if (byRider) riderRatingOfDriver = stars; else driverRatingOfRider = stars;
        touch(now);
        raise(RideRated.at(now, id().value(), byRider ? "RIDER" : "DRIVER", stars));
    }

    /** One tip per ride, within the rating window, at most 50% of the fare. */
    public void addTip(BigDecimal amount, Instant now) {
        if (status != RideStatus.COMPLETED || now.isAfter(completedAt.plus(RATING_WINDOW))) {
            throw new BusinessRuleViolation("This ride can no longer be tipped");
        }
        if (tip != null) {
            throw new BusinessRuleViolation("Already tipped");
        }
        if (amount.signum() <= 0 || amount.compareTo(finalFare.subtotal().multiply(new BigDecimal("0.5"))) > 0) {
            throw new BusinessRuleViolation("Invalid tip amount");
        }
        tip = amount.setScale(quote.fractionDigits(), java.math.RoundingMode.HALF_UP);
        touch(now);
        raise(RideTipped.at(now, id().value(), riderId.value(), driverId, tip, quote.currency()));
    }

    /**
     * Records a goodwill refund or fare correction by support. Idempotent per {@code ref}; the total refunded can never exceed what the
     * rider actually paid for this ride (the fare, or the cancellation fee).
     *
     * @return false if this ref was already applied
     */
    public boolean recordRefund(String ref, BigDecimal amount, Instant now) {
        final boolean paidFare = status == RideStatus.COMPLETED;
        final boolean paidFee = status == RideStatus.CANCELLED && cancelFee != null && cancelFee.signum() > 0;
        if (!paidFare && !paidFee) {
            throw new InvalidStateException("There is nothing to refund on a ride in state " + status);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleViolation("Refund must be positive");
        }
        if (refundRefs.contains(ref)) return false;
        final BigDecimal paid = paidFare ? finalFare.total() : cancelFee;
        final BigDecimal scaled = amount.setScale(quote.fractionDigits(), java.math.RoundingMode.HALF_UP);
        if (refunded.add(scaled).compareTo(paid) > 0) {
            throw new BusinessRuleViolation("Refund exceeds what the rider paid");
        }
        refunded = refunded.add(scaled);
        refundRefs.add(ref);
        while (refundRefs.size() > 50) refundRefs.remove(0);
        touch(now);
        raise(RideRefunded.at(now, id().value(), riderId.value(), scaled, refunded, quote.currency()));
        return true;
    }

    public void triggerSos(boolean byRider, Instant now) {
        if (status != RideStatus.DRIVER_ASSIGNED && status != RideStatus.DRIVER_ARRIVED && status != RideStatus.IN_PROGRESS) {
            throw new InvalidStateException("No active trip");
        }
        if (sosAt != null) return;
        sosAt = now;
        touch(now);
        raise(SosTriggered.at(now, id().value(), byRider ? "RIDER" : "DRIVER"));
    }

    /** Marks the money movements as done so a redelivered completion event cannot pay twice. */
    public boolean markSettled(Instant now) {
        if (settled) return false;
        if (status != RideStatus.COMPLETED && !(status == RideStatus.CANCELLED && cancelFee != null && cancelFee.signum() > 0)) {
            throw new InvalidStateException("Nothing to settle");
        }
        settled = true;
        touch(now);
        return true;
    }

    // ---------------------------------------------------------------- accessors

    public Optional<Offer> liveOffer() {
        return offers.stream().filter(o -> o.status() == Offer.Status.OFFERED).reduce((a, b) -> b);
    }

    public RiderId riderId() { return riderId; }
    public VehicleType vehicle() { return vehicle; }
    public Place pickup() { return pickup; }
    public Place dropoff() { return dropoff; }
    public FareQuote quote() { return quote; }
    public PaymentMethod paymentMethod() { return paymentMethod; }
    public RideStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public Instant searchDeadline() { return searchDeadline; }
    public List<Offer> offers() { return List.copyOf(offers); }
    public Optional<UUID> driverId() { return Optional.ofNullable(driverId); }
    public Instant assignedAt() { return assignedAt; }
    public Instant arrivedAt() { return arrivedAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public double trackedMeters() { return trackedMeters; }
    public FinalFare finalFare() { return finalFare; }
    public BigDecimal tip() { return tip; }
    public CancelledBy cancelledBy() { return cancelledBy; }
    public BigDecimal cancelFee() { return cancelFee; }
    public int pinAttempts() { return pinAttempts; }
    public int reassignments() { return reassignments; }
    public int riderRatingOfDriver() { return riderRatingOfDriver; }
    public int driverRatingOfRider() { return driverRatingOfRider; }
    public boolean settled() { return settled; }
    public Instant sosAt() { return sosAt; }
    public Instant cancelledAt() { return cancelledAt; }

    /** When the ride finished (completed or cancelled), or null while it is still going. */
    public Instant endedAt() { return completedAt != null ? completedAt : cancelledAt; }
    public int offRouteStreak() { return offRouteStreak; }
    public Instant deviationFlaggedAt() { return deviationFlaggedAt; }
    public BigDecimal refunded() { return refunded; }
    public LatLng lastPoint() { return lastPoint; }

    // ---------------------------------------------------------------- internals

    private static void requireUsableQuote(FareQuote q, VehicleType vehicle, Instant now) {
        if (q.vehicle() != vehicle) {
            throw new BusinessRuleViolation("Quote is for a different vehicle type");
        }
        if (q.isExpired(now)) {
            throw new BusinessRuleViolation("The price has expired; please request a new quote");
        }
    }

    private void requireStatus(RideStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateException("Cannot " + action + " a ride in state " + status);
        }
    }

    private void requireDriver(UUID byDriver) {
        if (driverId == null || !driverId.equals(byDriver)) {
            throw new BusinessRuleViolation("This ride is assigned to a different driver");
        }
    }

    private Offer requireLiveOfferFor(UUID driver) {
        requireStatus(RideStatus.SEARCHING, "respond to an offer for");
        return liveOffer().filter(o -> o.driverId().equals(driver))
                .orElseThrow(() -> new OfferExpiredException("There is no active offer for you on this ride"));
    }

    private void replace(Offer old, Offer next) {
        offers.replaceAll(o -> o == old ? next : o);
    }

    // ---------------------------------------------------------------- persistence

    public Snapshot snapshot() {
        return new Snapshot(id().value(), riderId.value(), vehicle.name(), pickup, dropoff, quote, paymentMethod.name(), pinHash, pinAttempts,
                status.name(), requestedAt, searchDeadline, new ArrayList<>(offers), driverId, assignedAt, arrivedAt, startedAt, completedAt,
                trackedMeters, lastPoint, lastPointAt, finalFare, tip, cancelledBy == null ? null : cancelledBy.name(), cancelReason, cancelFee,
                cancelledAt, reassignments, riderRatingOfDriver, driverRatingOfRider, settled, sosAt, offRouteStreak, deviationFlaggedAt,
                refunded, new ArrayList<>(refundRefs), createdAt, updatedAt, version);
    }

    public static Ride restore(Snapshot s) {
        final var r = new Ride(RideId.of(s.id()), RiderId.of(s.riderId()), VehicleType.valueOf(s.vehicle()), s.pickup(), s.dropoff(), s.quote(),
                PaymentMethod.valueOf(s.paymentMethod()), s.pinHash(), s.requestedAt(), s.searchDeadline());
        r.pinAttempts = s.pinAttempts();
        r.status = RideStatus.valueOf(s.status());
        r.offers.addAll(s.offers());
        r.driverId = s.driverId();
        r.assignedAt = s.assignedAt();
        r.arrivedAt = s.arrivedAt();
        r.startedAt = s.startedAt();
        r.completedAt = s.completedAt();
        r.trackedMeters = s.trackedMeters();
        r.lastPoint = s.lastPoint();
        r.lastPointAt = s.lastPointAt();
        r.finalFare = s.finalFare();
        r.tip = s.tip();
        r.cancelledBy = s.cancelledBy() == null ? null : CancelledBy.valueOf(s.cancelledBy());
        r.cancelReason = s.cancelReason();
        r.cancelFee = s.cancelFee();
        r.cancelledAt = s.cancelledAt();
        r.reassignments = s.reassignments();
        r.riderRatingOfDriver = s.riderRatingOfDriver();
        r.driverRatingOfRider = s.driverRatingOfRider();
        r.settled = s.settled();
        r.sosAt = s.sosAt();
        r.offRouteStreak = s.offRouteStreak();
        r.deviationFlaggedAt = s.deviationFlaggedAt();
        r.refunded = s.refunded() == null ? BigDecimal.ZERO : s.refunded();   // older documents predate refunds
        if (s.refundRefs() != null) r.refundRefs.addAll(s.refundRefs());
        r.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return r;
    }
}
