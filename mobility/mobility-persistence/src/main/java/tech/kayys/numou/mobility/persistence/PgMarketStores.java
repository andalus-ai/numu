package tech.kayys.numou.mobility.persistence;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.pricing.Promo;
import tech.kayys.numou.mobility.ride.spi.DemandCounter;
import tech.kayys.numou.mobility.ride.spi.PromoCatalog;
import tech.kayys.numou.mobility.ride.spi.SurgeStateStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Postgres demand counter, surge state and promo catalog. All statements verified against PostgreSQL 16 (see V300 tests). NOT COMPILED in sandbox. */
public final class PgMarketStores {

    private PgMarketStores() { }

    @ApplicationScoped
    public static class Demand implements DemandCounter {
        static final String INCREMENT = """
                INSERT INTO ride_demand (tenant_id, cell, bucket, n) VALUES ($1, $2, $3, 1)
                ON CONFLICT (tenant_id, cell, bucket) DO UPDATE SET n = ride_demand.n + 1""";
        static final String COUNT = "SELECT coalesce(sum(n), 0)::int AS total FROM ride_demand WHERE tenant_id = $1 AND cell = ANY($2) AND bucket >= $3";

        @Inject
        PgPool pool;

        @Override
        public Uni<Void> record(UUID tenantId, GeoCell cell, Instant at) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(INCREMENT)
                    .execute(Tuple.of(tenantId, cell.key(), at.truncatedTo(ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC)))).replaceWithVoid();
        }

        @Override
        public Uni<Integer> count(UUID tenantId, Collection<GeoCell> cells, Instant since) {
            final String[] keys = cells.stream().map(GeoCell::key).toArray(String[]::new);
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(COUNT).execute(Tuple.tuple()
                            .addUUID(tenantId).addArrayOfString(keys).addOffsetDateTime(since.truncatedTo(ChronoUnit.MINUTES).atOffset(ZoneOffset.UTC))))
                    .map(rows -> rows.iterator().next().getInteger("total"));
        }
    }

    @ApplicationScoped
    public static class SurgeState implements SurgeStateStore {
        static final String GET = "SELECT multiplier, updated_at FROM ride_surge_state WHERE tenant_id = $1 AND cell = $2";
        static final String PUT = """
                INSERT INTO ride_surge_state (tenant_id, cell, multiplier, updated_at) VALUES ($1, $2, $3, $4)
                ON CONFLICT (tenant_id, cell) DO UPDATE SET multiplier = EXCLUDED.multiplier, updated_at = EXCLUDED.updated_at""";

        @Inject
        PgPool pool;

        @Override
        public Uni<Optional<State>> get(UUID tenantId, GeoCell cell) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(GET).execute(Tuple.of(tenantId, cell.key())))
                    .map(rows -> rows.iterator().hasNext()
                            ? Optional.of(new State(rows.iterator().next().getBigDecimal("multiplier"), rows.iterator().next().getOffsetDateTime("updated_at").toInstant()))
                            : Optional.empty());
        }

        @Override
        public Uni<Void> put(UUID tenantId, GeoCell cell, State s) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(PUT)
                    .execute(Tuple.of(tenantId, cell.key(), s.multiplier(), s.updatedAt().atOffset(ZoneOffset.UTC)))).replaceWithVoid();
        }
    }

    @ApplicationScoped
    public static class Promos implements PromoCatalog {
        static final String FIND = "SELECT code, promo_type, value, max_discount, min_subtotal, valid_from, valid_until FROM ride_promo WHERE tenant_id = $1 AND code = $2";
        /** The primary key (tenant, rider, code) is the once-per-rider rule; DO NOTHING + RETURNING tells us whether we won. */
        static final String REDEEM = "INSERT INTO ride_promo_redemption (tenant_id, rider_id, code, ride_id) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING RETURNING ride_id";
        static final String EXISTING = "SELECT ride_id FROM ride_promo_redemption WHERE tenant_id = $1 AND rider_id = $2 AND code = $3";
        static final String REFUND = "DELETE FROM ride_promo_redemption WHERE tenant_id = $1 AND rider_id = $2 AND code = $3 AND ride_id = $4";

        @Inject
        PgPool pool;

        @Override
        public Uni<Optional<Promo>> find(UUID tenantId, String code, Instant now) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(FIND).execute(Tuple.of(tenantId, code))).map(rows -> {
                if (!rows.iterator().hasNext()) return Optional.<Promo>empty();
                final Row r = rows.iterator().next();
                return Optional.of(new Promo(r.getString("code"), Promo.Type.valueOf(r.getString("promo_type")), r.getBigDecimal("value"),
                        r.getBigDecimal("max_discount"), r.getBigDecimal("min_subtotal"), r.getOffsetDateTime("valid_from").toInstant(),
                        r.getOffsetDateTime("valid_until").toInstant()));
            });
        }

        @Override
        public Uni<Boolean> redeem(UUID tenantId, UUID riderId, String code, UUID rideId) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(REDEEM).execute(Tuple.of(tenantId, riderId, code, rideId)).flatMap(won -> {
                if (won.iterator().hasNext()) return Uni.createFrom().item(true);
                return c.preparedQuery(EXISTING).execute(Tuple.of(tenantId, riderId, code))
                        .map(rows -> rows.iterator().hasNext() && rideId.equals(rows.iterator().next().getUUID("ride_id")));   // same ride retrying: still ok
            }));
        }

        @Override
        public Uni<Void> refund(UUID tenantId, UUID riderId, String code, UUID rideId) {
            return PgTenantPool.run(pool, tenantId, c -> c.preparedQuery(REFUND).execute(Tuple.of(tenantId, riderId, code, rideId))).replaceWithVoid();
        }
    }
}
