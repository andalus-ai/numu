package tech.kayys.numou.mobility.driver.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where drivers are, right now. Deliberately <em>not</em> an aggregate: positions change every few seconds, are worthless
 * minutes later, and must never go through the transactional outbox. The aggregate remains the source of truth for whether
 * a driver may take a ride; the index only narrows "who is nearby" cheaply.
 *
 * <p>Implementations: in-memory (tests/dev), Postgres UNLOGGED table (default production), Redis GEO (at larger scale).
 */
public interface DriverLocationIndex {

    record Fix(DriverId driverId, VehicleType vehicle, LatLng at, double headingDegrees, double speedKmh, Instant recordedAt, boolean available) { }

    record Nearby(Fix fix, double distanceMeters) { }

    Uni<Void> upsert(UUID tenantId, Fix fix);

    /** Flips the availability flag without moving the driver (going offline / on trip / back online). */
    Uni<Void> setAvailable(UUID tenantId, DriverId driverId, boolean available);

    Uni<Void> remove(UUID tenantId, DriverId driverId);

    Uni<Optional<Fix>> last(UUID tenantId, DriverId driverId);

    /** Available drivers of the vehicle class within the radius whose fix is newer than {@code freshSince}, nearest first. */
    Uni<List<Nearby>> nearby(UUID tenantId, LatLng center, double radiusMeters, VehicleType vehicle, Instant freshSince, int limit);
}
