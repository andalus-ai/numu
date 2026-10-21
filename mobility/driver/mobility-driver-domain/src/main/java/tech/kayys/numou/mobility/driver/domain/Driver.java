package tech.kayys.numou.mobility.driver.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.mobility.driver.domain.event.DriverApproved;
import tech.kayys.numou.mobility.driver.domain.event.DriverRegistered;
import tech.kayys.numou.mobility.driver.domain.event.DriverRejected;
import tech.kayys.numou.mobility.driver.domain.event.DriverSuspended;
import tech.kayys.numou.mobility.driver.domain.event.DriverWentOffline;
import tech.kayys.numou.mobility.driver.domain.event.DriverWentOnline;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A driver-partner. Dispatch only ever offers rides to a driver who is APPROVED, ONLINE and not suspended; the
 * authoritative check happens in {@link #claim}, which flips ONLINE to ON_TRIP under the aggregate's optimistic lock, so two
 * rides racing for the same driver cannot both win.
 *
 * <p>Quality control: acceptance rate and rating feed dispatch ranking; cancelling too many accepted rides in a day
 * suspends the driver for a cool-off period.
 */
public final class Driver extends VersionedAggregate<DriverId> {

    private static final long serialVersionUID = 1L;
    public static final int CANCELLATIONS_BEFORE_SUSPENSION = 3;
    public static final Duration CANCELLATION_WINDOW = Duration.ofHours(24);
    public static final Duration SUSPENSION = Duration.ofHours(2);
    static final int MIN_SAMPLES = 5;

    public enum Verification { PENDING, APPROVED, REJECTED }

    public enum Status { OFFLINE, ONLINE, ON_TRIP }

    public record Snapshot(UUID id, String name, String phone, String vehicleType, String plate, String model, String color,
                           String verification, String status, UUID activeRideId, long ratingSumTenths, int ratingCount,
                           int offersReceived, int offersAccepted, List<Instant> recentCancellations, Instant suspendedUntil,
                           Instant createdAt, Instant updatedAt, int version) { }

    private final String name;
    private final String phone;
    private final Vehicle vehicle;
    private Verification verification = Verification.PENDING;
    private Status status = Status.OFFLINE;
    private RideId activeRideId;
    private long ratingSumTenths;      // stars x10 as integers: exact arithmetic, no float drift
    private int ratingCount;
    private int offersReceived;
    private int offersAccepted;
    private final List<Instant> recentCancellations = new ArrayList<>();
    private Instant suspendedUntil;

    private Driver(DriverId id, String name, String phone, Vehicle vehicle, Instant now) {
        super(id, now);
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("Driver name must be 1-120 characters");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Driver phone is required");
        }
        this.name = name.trim();
        this.phone = phone.trim();
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle cannot be null");
    }

    public static Driver register(DriverId id, String name, String phone, Vehicle vehicle, Instant now) {
        final var d = new Driver(id, name, phone, vehicle, now);
        d.raise(DriverRegistered.at(now, id.value(), vehicle.type().name()));
        return d;
    }

    // ---------------------------------------------------------------- onboarding

    public void approve(Instant now) {
        if (verification == Verification.APPROVED) return;
        verification = Verification.APPROVED;
        touch(now);
        raise(DriverApproved.at(now, id().value()));
    }

    public void reject(String reason, Instant now) {
        if (status != Status.OFFLINE) {
            throw new InvalidStateException("Take the driver offline before rejecting");
        }
        verification = Verification.REJECTED;
        touch(now);
        raise(DriverRejected.at(now, id().value(), reason == null ? "" : reason));
    }

    // ---------------------------------------------------------------- availability

    public void goOnline(Instant now) {
        if (verification != Verification.APPROVED) {
            throw new DriverUnavailableException("Driver is not verified");
        }
        if (isSuspended(now)) {
            throw new DriverUnavailableException("Driver is suspended until " + suspendedUntil);
        }
        if (status == Status.ONLINE || status == Status.ON_TRIP) return;
        status = Status.ONLINE;
        touch(now);
        raise(DriverWentOnline.at(now, id().value(), vehicle.type().name()));
    }

    public void goOffline(Instant now) {
        if (status == Status.ON_TRIP) {
            throw new InvalidStateException("Finish the current trip before going offline");
        }
        if (status == Status.OFFLINE) return;
        status = Status.OFFLINE;
        touch(now);
        raise(DriverWentOffline.at(now, id().value()));
    }

    /** Reserves the driver for a ride. Fails if they are not ONLINE right now: this is the double-assignment guard. */
    public void claim(RideId rideId, Instant now) {
        if (activeRideId != null && activeRideId.equals(rideId)) return;        // idempotent for the same ride
        if (verification != Verification.APPROVED || isSuspended(now) || status != Status.ONLINE) {
            throw new DriverUnavailableException("Driver is not available");
        }
        status = Status.ON_TRIP;
        activeRideId = rideId;
        touch(now);
    }

    /** Frees the driver after a ride ends or is cancelled. A release for a different ride is ignored (late/duplicate events). */
    public boolean release(RideId rideId, Instant now) {
        if (activeRideId == null || !activeRideId.equals(rideId)) return false;
        status = Status.ONLINE;
        activeRideId = null;
        touch(now);
        return true;
    }

    // ---------------------------------------------------------------- quality

    public void recordOffer(boolean accepted, Instant now) {
        offersReceived++;
        if (accepted) offersAccepted++;
        touch(now);
    }

    /** @param stars 1..5 */
    public void recordRating(int stars, Instant now) {
        if (stars < 1 || stars > 5) {
            throw new BusinessRuleViolation("Rating must be 1-5");
        }
        ratingSumTenths += stars * 10L;
        ratingCount++;
        touch(now);
    }

    /** The driver cancelled an accepted ride. Repeated cancellations trigger a cool-off suspension. */
    public void recordCancellation(Instant now) {
        recentCancellations.add(now);
        recentCancellations.removeIf(t -> t.isBefore(now.minus(CANCELLATION_WINDOW)));
        if (recentCancellations.size() >= CANCELLATIONS_BEFORE_SUSPENSION) {
            suspendedUntil = now.plus(SUSPENSION);
            recentCancellations.clear();
            raise(DriverSuspended.at(now, id().value(), "too many cancellations", suspendedUntil));
        }
        touch(now);
    }

    public boolean isSuspended(Instant now) {
        return suspendedUntil != null && now.isBefore(suspendedUntil);
    }

    /** Offers received that were accepted. Neutral (1.0) until there is enough history to mean something. */
    public double acceptanceRate() {
        return offersReceived < MIN_SAMPLES ? 1.0 : (double) offersAccepted / offersReceived;
    }

    /** Average stars. Neutral 4.8 until there is enough history, so a new driver is neither favoured nor punished. */
    public double rating() {
        return ratingCount < MIN_SAMPLES ? 4.8 : ratingSumTenths / 10.0 / ratingCount;
    }

    public boolean isDispatchable(Instant now) {
        return verification == Verification.APPROVED && status == Status.ONLINE && !isSuspended(now);
    }

    public String name() { return name; }
    public String phone() { return phone; }
    public Vehicle vehicle() { return vehicle; }
    public Verification verification() { return verification; }
    public Status status() { return status; }
    public RideId activeRideId() { return activeRideId; }
    public Instant suspendedUntil() { return suspendedUntil; }
    public int ratingCount() { return ratingCount; }

    // ---------------------------------------------------------------- persistence

    public Snapshot snapshot() {
        return new Snapshot(id().value(), name, phone, vehicle.type().name(), vehicle.plate(), vehicle.model(), vehicle.color(),
                verification.name(), status.name(), activeRideId == null ? null : activeRideId.value(), ratingSumTenths, ratingCount,
                offersReceived, offersAccepted, new ArrayList<>(recentCancellations), suspendedUntil, createdAt, updatedAt, version);
    }

    public static Driver restore(Snapshot s) {
        final var d = new Driver(DriverId.of(s.id()), s.name(), s.phone(),
                new Vehicle(tech.kayys.numou.mobility.kernel.domain.VehicleType.valueOf(s.vehicleType()), s.plate(), s.model(), s.color()), s.createdAt());
        d.verification = Verification.valueOf(s.verification());
        d.status = Status.valueOf(s.status());
        d.activeRideId = s.activeRideId() == null ? null : RideId.of(s.activeRideId());
        d.ratingSumTenths = s.ratingSumTenths();
        d.ratingCount = s.ratingCount();
        d.offersReceived = s.offersReceived();
        d.offersAccepted = s.offersAccepted();
        d.recentCancellations.addAll(s.recentCancellations());
        d.suspendedUntil = s.suspendedUntil();
        d.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return d;
    }
}
