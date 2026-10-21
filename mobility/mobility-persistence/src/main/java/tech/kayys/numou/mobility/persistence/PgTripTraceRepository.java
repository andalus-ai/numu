package tech.kayys.numou.mobility.persistence;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.ride.domain.TracePoint;
import tech.kayys.numou.mobility.ride.spi.TripTraceRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

/** Insert-only GPS trace (table {@code trip_trace_point}, V301; RLS verified). NOT COMPILED in the authoring sandbox. */
@ApplicationScoped
public class PgTripTraceRepository implements TripTraceRepository {

    static final String INSERT = "INSERT INTO trip_trace_point (tenant_id, ride_id, at, lat, lng) VALUES ($1, $2, $3, $4, $5)";
    static final String LOAD = "SELECT at, lat, lng FROM trip_trace_point WHERE tenant_id = $1 AND ride_id = $2 ORDER BY at";

    @Inject
    PgPool pool;

    @Override
    public Uni<Void> append(UUID tenantId, RideId rideId, List<TracePoint> points) {
        if (points.isEmpty()) return Uni.createFrom().voidItem();
        final var rows = points.stream().map(p -> Tuple.tuple().addUUID(tenantId).addUUID(rideId.value())
                .addOffsetDateTime(p.time().atOffset(ZoneOffset.UTC)).addDouble(p.at().lat()).addDouble(p.at().lng())).toList();
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(INSERT).executeBatch(rows)).replaceWithVoid();
    }

    @Override
    public Uni<List<TracePoint>> load(UUID tenantId, RideId rideId) {
        return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(LOAD).execute(Tuple.of(tenantId, rideId.value())))
                .map(rows -> StreamSupport.stream(rows.spliterator(), false)
                        .map(r -> new TracePoint(new LatLng(r.getDouble("lat"), r.getDouble("lng")), r.getOffsetDateTime("at").toInstant())).toList());
    }
}
