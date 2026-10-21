package tech.kayys.numou.mobility.driver.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.domain.Driver;
import tech.kayys.numou.mobility.driver.domain.Vehicle;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.driver.spi.DriverRepository;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.MovementRules;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/** Driver onboarding, availability and live location. */
public final class DriverService {

    private static final int MAX_ATTEMPTS = 5;
    /** A move smaller than this within {@link #MIN_WRITE_INTERVAL} of the last stored fix is not worth a write. */
    private static final double MIN_MOVE_METERS = 15.0;
    private static final Duration MIN_WRITE_INTERVAL = Duration.ofSeconds(3);

    public enum LocationOutcome { STORED, SKIPPED_UNCHANGED, REJECTED_IMPLAUSIBLE, REJECTED_STALE, REJECTED_NOT_ONLINE }

    public record LocationResult(LocationOutcome outcome, UUID activeRideId) { }

    private final DriverRepository drivers;
    private final DriverLocationIndex index;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;

    public DriverService(DriverRepository drivers, DriverLocationIndex index, UnitOfWork uow, DomainEventSink events, DomainClock clock) {
        this.drivers = drivers;
        this.index = index;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- onboarding

    public Uni<DriverView> register(UUID tenantId, UUID driverId, String name, String phone, Vehicle vehicle) {
        return uow.execute(() -> {
            final var d = Driver.register(DriverId.of(driverId), name, phone, vehicle, clock.now());
            return drivers.save(tenantId, d).flatMap(x -> events.publish(tenantId, d.pullDomainEvents())).replaceWith(DriverView.of(d));
        });
    }

    public Uni<DriverView> approve(UUID tenantId, UUID driverId) {
        return change(tenantId, driverId, d -> d.approve(clock.now()));
    }

    public Uni<DriverView> reject(UUID tenantId, UUID driverId, String reason) {
        return change(tenantId, driverId, d -> d.reject(reason, clock.now()));
    }

    // ---------------------------------------------------------------- availability

    public Uni<DriverView> goOnline(UUID tenantId, UUID driverId) {
        return change(tenantId, driverId, d -> d.goOnline(clock.now()))
                .flatMap(v -> index.setAvailable(tenantId, DriverId.of(driverId), true).replaceWith(v));
    }

    public Uni<DriverView> goOffline(UUID tenantId, UUID driverId) {
        return change(tenantId, driverId, d -> d.goOffline(clock.now()))
                .flatMap(v -> index.remove(tenantId, DriverId.of(driverId)).replaceWith(v));
    }

    /** Reserves the driver for a ride and takes them out of the searchable pool. Throws if they are no longer available. */
    public Uni<DriverView> claim(UUID tenantId, UUID driverId, RideId rideId) {
        return change(tenantId, driverId, d -> d.claim(rideId, clock.now()))
                .flatMap(v -> index.setAvailable(tenantId, DriverId.of(driverId), false).replaceWith(v));
    }

    /** Puts the driver back in the pool after a ride. Safe to call twice and for the wrong ride. */
    public Uni<DriverView> release(UUID tenantId, UUID driverId, RideId rideId) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, driverId).flatMap(d -> {
            final boolean released = d.release(rideId, clock.now());
            if (!released) {
                return Uni.createFrom().item(DriverView.of(d));
            }
            return drivers.save(tenantId, d).flatMap(x -> events.publish(tenantId, d.pullDomainEvents()))
                    .replaceWith(DriverView.of(d));
        }))).flatMap(v -> "ONLINE".equals(v.status())
                ? index.setAvailable(tenantId, DriverId.of(driverId), true).replaceWith(v)
                : Uni.createFrom().item(v));
    }

    public Uni<DriverView> recordOffer(UUID tenantId, UUID driverId, boolean accepted) {
        return change(tenantId, driverId, d -> d.recordOffer(accepted, clock.now()));
    }

    public Uni<DriverView> recordRating(UUID tenantId, UUID driverId, int stars) {
        return change(tenantId, driverId, d -> d.recordRating(stars, clock.now()));
    }

    public Uni<DriverView> recordCancellation(UUID tenantId, UUID driverId) {
        return change(tenantId, driverId, d -> d.recordCancellation(clock.now()))
                .flatMap(v -> "ONLINE".equals(v.status()) ? Uni.createFrom().item(v) : index.remove(tenantId, DriverId.of(driverId)).replaceWith(v));
    }

    public Uni<DriverView> get(UUID tenantId, UUID driverId) {
        return load(tenantId, driverId).map(DriverView::of);
    }

    // ---------------------------------------------------------------- live location

    /**
     * Ingests a GPS fix from the driver app. Not transactional and not evented: it is the highest-volume write in the system.
     * Rejects fixes that are stale, out of order or physically impossible (teleports), and skips ones that add nothing.
     */
    public Uni<LocationResult> updateLocation(UUID tenantId, UUID driverId, LatLng at, double heading, double speedKmh, Instant recordedAt) {
        final var id = DriverId.of(driverId);
        final var now = clock.now();
        if (recordedAt.isAfter(now.plusSeconds(30)) || recordedAt.isBefore(now.minus(Duration.ofSeconds(30)))) {
            return Uni.createFrom().item(new LocationResult(LocationOutcome.REJECTED_STALE, null)); // device clock far off, or replayed old data
        }
        return load(tenantId, driverId).flatMap(d -> {
            final UUID active = d.activeRideId() == null ? null : d.activeRideId().value();
            if (d.status() == Driver.Status.OFFLINE) {
                return Uni.createFrom().item(new LocationResult(LocationOutcome.REJECTED_NOT_ONLINE, null));
            }
            return index.last(tenantId, id).flatMap(prev -> {
                if (prev.isPresent()) {
                    final var p = prev.get();
                    if (!MovementRules.isPlausible(p.at(), p.recordedAt(), at, recordedAt, d.vehicle().type())) {
                        return Uni.createFrom().item(new LocationResult(LocationOutcome.REJECTED_IMPLAUSIBLE, active));
                    }
                    final boolean tiny = p.at().distanceTo(at) < MIN_MOVE_METERS && Duration.between(p.recordedAt(), recordedAt).compareTo(MIN_WRITE_INTERVAL) < 0;
                    if (tiny) {
                        return Uni.createFrom().item(new LocationResult(LocationOutcome.SKIPPED_UNCHANGED, active));
                    }
                }
                final boolean available = d.status() == Driver.Status.ONLINE;
                return index.upsert(tenantId, new DriverLocationIndex.Fix(id, d.vehicle().type(), at, heading, speedKmh, recordedAt, available))
                        .replaceWith(new LocationResult(LocationOutcome.STORED, active));
            });
        });
    }

    // ---------------------------------------------------------------- internals

    private Uni<DriverView> change(UUID tenantId, UUID driverId, Consumer<Driver> action) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> load(tenantId, driverId).flatMap(d -> {
            action.accept(d);
            return drivers.save(tenantId, d).flatMap(x -> {
                final var raised = d.pullDomainEvents();
                return raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised);
            }).replaceWith(DriverView.of(d));
        })));
    }

    private Uni<Driver> load(UUID tenantId, UUID driverId) {
        return drivers.find(tenantId, DriverId.of(driverId)).map(o -> o.orElseThrow(() -> new NotFoundException("Driver", driverId)));
    }
}
