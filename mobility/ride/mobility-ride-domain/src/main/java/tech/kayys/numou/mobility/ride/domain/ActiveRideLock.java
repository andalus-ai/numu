package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.aggregate.domain.VersionedAggregate;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * "This rider already has an active ride." One tiny aggregate per rider, keyed by the rider id, so two simultaneous booking
 * taps collide on the same primary key / version and exactly one wins: the same trick as the reservation ledger.
 *
 * <p>The lock stores only the ride id. If a crash left it pointing at a ride that has since ended, the service checks that
 * ride's status and lets the new booking take the lock over.
 */
public final class ActiveRideLock extends VersionedAggregate<RiderId> {

    private static final long serialVersionUID = 1L;

    public record Snapshot(UUID riderId, UUID activeRideId, Instant createdAt, Instant updatedAt, int version) { }

    private RideId activeRideId;

    private ActiveRideLock(RiderId id, Instant now) {
        super(id, now);
    }

    public static ActiveRideLock openFor(RiderId riderId, Instant now) {
        return new ActiveRideLock(riderId, now);
    }

    public Optional<RideId> holder() {
        return Optional.ofNullable(activeRideId);
    }

    /** @throws ActiveRideExistsException if a different ride holds the lock */
    public void acquire(RideId rideId, Instant now) {
        if (activeRideId != null && !activeRideId.equals(rideId)) {
            throw new ActiveRideExistsException();
        }
        activeRideId = rideId;
        touch(now);
    }

    /** Takes over a lock whose ride has already ended (only the service decides that). */
    public void takeOver(RideId rideId, Instant now) {
        activeRideId = rideId;
        touch(now);
    }

    /** Releases only if this ride holds it. A late release for an old ride must not free a newer ride's lock. */
    public boolean release(RideId rideId, Instant now) {
        if (activeRideId == null || !activeRideId.equals(rideId)) return false;
        activeRideId = null;
        touch(now);
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), activeRideId == null ? null : activeRideId.value(), createdAt, updatedAt, version);
    }

    public static ActiveRideLock restore(Snapshot s) {
        final var l = new ActiveRideLock(RiderId.of(s.riderId()), s.createdAt());
        l.activeRideId = s.activeRideId() == null ? null : RideId.of(s.activeRideId());
        l.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return l;
    }
}
