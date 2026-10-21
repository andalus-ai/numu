package tech.kayys.numou.mobility.persistence;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

/**
 * Postgres driver-location index on an UNLOGGED table (see V300). One upsert per driver update, one indexed query per dispatch
 * step: cells narrow the candidates, then exact haversine distance is applied in Java. For fleets beyond what a single
 * Postgres comfortably absorbs in writes (order of 10k+ updates/second), swap in a Redis GEO implementation of the same port.
 * SQL verified against PostgreSQL 16. NOT COMPILED in the authoring sandbox.
 */
@ApplicationScoped
public class PgDriverLocationIndex implements DriverLocationIndex {

    static final String UPSERT = """
            INSERT INTO driver_location (tenant_id, driver_id, vehicle, cell, lat, lng, heading, speed_kmh, recorded_at, available)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            ON CONFLICT (tenant_id, driver_id) DO UPDATE SET vehicle = EXCLUDED.vehicle, cell = EXCLUDED.cell, lat = EXCLUDED.lat,
                lng = EXCLUDED.lng, heading = EXCLUDED.heading, speed_kmh = EXCLUDED.speed_kmh, recorded_at = EXCLUDED.recorded_at,
                available = EXCLUDED.available""";
    static final String SET_AVAILABLE = "UPDATE driver_location SET available = $3 WHERE tenant_id = $1 AND driver_id = $2";
    static final String REMOVE = "DELETE FROM driver_location WHERE tenant_id = $1 AND driver_id = $2";
    static final String LAST = """
            SELECT driver_id, vehicle, lat, lng, heading, speed_kmh, recorded_at, available FROM driver_location
            WHERE tenant_id = $1 AND driver_id = $2""";
    static final String NEARBY = """
            SELECT driver_id, vehicle, lat, lng, heading, speed_kmh, recorded_at, available FROM driver_location
            WHERE tenant_id = $1 AND vehicle = $2 AND available AND cell = ANY($3) AND recorded_at >= $4""";

    @Inject
    PgPool pool;

    @Override
    public Uni<Void> upsert(UUID tenantId, Fix f) {
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(UPSERT).execute(Tuple.tuple()
                .addUUID(tenantId).addUUID(f.driverId().value()).addString(f.vehicle().name()).addString(GeoCell.of(f.at()).key())
                .addDouble(f.at().lat()).addDouble(f.at().lng()).addFloat((float) f.headingDegrees()).addFloat((float) f.speedKmh())
                .addOffsetDateTime(utc(f.recordedAt())).addBoolean(f.available()))).replaceWithVoid();
    }

    @Override
    public Uni<Void> setAvailable(UUID tenantId, DriverId id, boolean available) {
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(SET_AVAILABLE)
                .execute(Tuple.of(tenantId, id.value(), available))).replaceWithVoid();
    }

    @Override
    public Uni<Void> remove(UUID tenantId, DriverId id) {
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(REMOVE).execute(Tuple.of(tenantId, id.value()))).replaceWithVoid();
    }

    @Override
    public Uni<Optional<Fix>> last(UUID tenantId, DriverId id) {
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(LAST).execute(Tuple.of(tenantId, id.value())))
                .map(rows -> rows.iterator().hasNext() ? Optional.of(toFix(rows.iterator().next())) : Optional.empty());
    }

    @Override
    public Uni<List<Nearby>> nearby(UUID tenantId, LatLng center, double radiusMeters, VehicleType vehicle, Instant freshSince, int limit) {
        final String[] cells = GeoCell.around(center, radiusMeters).stream().map(GeoCell::key).toArray(String[]::new);
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(NEARBY).execute(Tuple.tuple()
                        .addUUID(tenantId).addString(vehicle.name()).addArrayOfString(cells).addOffsetDateTime(utc(freshSince))))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false).map(PgDriverLocationIndex::toFix)
                        .map(f -> new Nearby(f, center.distanceTo(f.at())))
                        .filter(n -> n.distanceMeters() <= radiusMeters)
                        .sorted(Comparator.comparingDouble(Nearby::distanceMeters).thenComparing(n -> n.fix().driverId().value()))
                        .limit(limit).toList());
    }

    private static Fix toFix(Row r) {
        return new Fix(DriverId.of(r.getUUID("driver_id")), VehicleType.valueOf(r.getString("vehicle")),
                new LatLng(r.getDouble("lat"), r.getDouble("lng")), r.getFloat("heading"), r.getFloat("speed_kmh"),
                r.getOffsetDateTime("recorded_at").toInstant(), r.getBoolean("available"));
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
