package tech.kayys.numou.mobility.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.persistence.PgDriverLocationIndex;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL contract tests for the production driver-location adapter.
 * Run with -Ppg-integration against the dedicated, migrated Mobility test database.
 */
@QuarkusTest
class DriverLocationPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LatLng CENTER = new LatLng(0.0, 0.0);

    @Inject
    PgDriverLocationIndex index;

    @Inject
    PgPool pool;

    @Test
    @RunOnVertxContext
    void nearbyEnforcesTenantVehicleAvailabilityFreshnessExactRadiusOrderingAndLimit(UniAsserter asserter) {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var nearest = fix("00000000-0000-0000-0000-000000000101", 0.0005, 0.0, VehicleType.CAR, true, NOW);
        var farther = fix("00000000-0000-0000-0000-000000000102", 0.0010, 0.0, VehicleType.CAR, true, NOW);
        var outsideRadius = fix("00000000-0000-0000-0000-000000000103", 0.0049, 0.0, VehicleType.CAR, true, NOW);
        var unavailable = fix("00000000-0000-0000-0000-000000000104", 0.0002, 0.0, VehicleType.CAR, false, NOW);
        var wrongVehicle = fix("00000000-0000-0000-0000-000000000105", 0.0001, 0.0, VehicleType.BIKE, true, NOW);
        var stale = fix("00000000-0000-0000-0000-000000000106", 0.0001, 0.0, VehicleType.CAR, true, NOW.minusSeconds(1));
        var otherTenant = fix("00000000-0000-0000-0000-000000000107", 0.0001, 0.0, VehicleType.CAR, true, NOW);

        asserter.execute(() -> index.upsert(tenantA, nearest));
        asserter.execute(() -> index.upsert(tenantA, farther));
        asserter.execute(() -> index.upsert(tenantA, outsideRadius));
        asserter.execute(() -> index.upsert(tenantA, unavailable));
        asserter.execute(() -> index.upsert(tenantA, wrongVehicle));
        asserter.execute(() -> index.upsert(tenantA, stale));
        asserter.execute(() -> index.upsert(tenantB, otherTenant));

        asserter.assertThat(() -> index.nearby(tenantA, CENTER, 500.0, VehicleType.CAR, NOW, 10), results -> {
            assertEquals(List.of(nearest.driverId(), farther.driverId()),
                    results.stream().map(n -> n.fix().driverId()).toList());
            assertTrue(results.get(0).distanceMeters() <= results.get(1).distanceMeters());
        });
        asserter.assertThat(() -> index.nearby(tenantA, CENTER, 500.0, VehicleType.CAR, NOW, 1), results ->
                assertEquals(List.of(nearest.driverId()), results.stream().map(n -> n.fix().driverId()).toList()));
    }

    @Test
    @RunOnVertxContext
    void upsertMovesTheIndexedCellAndRemoveClearsThePersistedFix(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        var id = DriverId.of("00000000-0000-0000-0000-000000000121");
        var original = new DriverLocationIndex.Fix(id, VehicleType.CAR, new LatLng(0.0005, 0.0),
                0.0, 0.0, NOW, true);
        var moved = new DriverLocationIndex.Fix(id, VehicleType.CAR, new LatLng(0.0200, 0.0),
                0.0, 0.0, NOW.plusSeconds(5), true);

        asserter.execute(() -> index.upsert(tenant, original));
        asserter.execute(() -> index.upsert(tenant, moved));
        asserter.assertThat(() -> index.nearby(tenant, CENTER, 500.0, VehicleType.CAR, NOW, 10),
                results -> assertTrue(results.isEmpty()));
        asserter.assertThat(() -> index.last(tenant, id), last -> {
            assertTrue(last.isPresent());
            assertEquals(moved, last.get());
        });
        asserter.execute(() -> index.remove(tenant, id));
        asserter.assertThat(() -> index.last(tenant, id), last -> assertTrue(last.isEmpty()));
        asserter.assertThat(() -> index.nearby(tenant, moved.at(), 500.0, VehicleType.CAR, NOW, 10),
                results -> assertTrue(results.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void availabilityMutationIsTenantScopedAndDoesNotChangeCoordinates(UniAsserter asserter) {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var fix = fix("00000000-0000-0000-0000-000000000131", 0.0005, 0.0, VehicleType.CAR, true, NOW);

        asserter.execute(() -> index.upsert(tenantA, fix));
        asserter.execute(() -> index.setAvailable(tenantB, fix.driverId(), false));
        asserter.assertThat(() -> index.last(tenantA, fix.driverId()), last -> {
            assertTrue(last.isPresent());
            assertTrue(last.get().available());
        });
        asserter.execute(() -> index.setAvailable(tenantA, fix.driverId(), false));
        asserter.assertThat(() -> index.last(tenantA, fix.driverId()), last -> {
            assertTrue(last.isPresent());
            assertEquals(fix.at(), last.get().at());
            assertFalse(last.get().available());
        });
        asserter.assertThat(() -> index.nearby(tenantA, CENTER, 500.0, VehicleType.CAR, NOW, 10),
                results -> assertTrue(results.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void rowLevelSecurityScopesRowsToTransactionTenantAndFailsClosedWithoutTenant(UniAsserter asserter) {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var fixA = fix("00000000-0000-0000-0000-000000000141", 0.0005, 0.0, VehicleType.CAR, true, NOW);
        var fixB = fix("00000000-0000-0000-0000-000000000142", 0.0006, 0.0, VehicleType.CAR, true, NOW);

        asserter.execute(() -> index.upsert(tenantA, fixA));
        asserter.execute(() -> index.upsert(tenantB, fixB));
        asserter.assertThat(() -> visibleLocationCount(null), count -> assertEquals(0L, count));
        asserter.assertThat(() -> visibleLocationCount(tenantA), count -> assertEquals(1L, count));
        asserter.assertThat(() -> visibleLocationCount(tenantB), count -> assertEquals(1L, count));
        asserter.execute(() -> index.remove(tenantA, fixA.driverId()));
        asserter.execute(() -> index.remove(tenantB, fixB.driverId()));
    }

    private Uni<Long> visibleLocationCount(UUID tenantId) {
        return pool.withTransaction(connection -> {
            Uni<Void> setTenant = tenantId == null
                    ? Uni.createFrom().voidItem()
                    : connection.preparedQuery("SELECT set_config('app.tenant_id', $1, true)")
                            .execute(Tuple.of(tenantId.toString()))
                            .replaceWithVoid();
            return setTenant
                    .flatMap(ignored -> connection.query("SELECT count(*) AS total FROM driver_location").execute())
                    .map(rows -> rows.iterator().next().getLong("total"));
        });
    }

    private static DriverLocationIndex.Fix fix(
            String id, double latitude, double longitude, VehicleType vehicle, boolean available, Instant recordedAt) {
        return new DriverLocationIndex.Fix(
                DriverId.of(id), vehicle, new LatLng(latitude, longitude), 0.0, 0.0, recordedAt, available);
    }
}
