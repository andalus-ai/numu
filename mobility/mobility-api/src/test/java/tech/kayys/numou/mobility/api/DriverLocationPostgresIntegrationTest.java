package tech.kayys.numou.mobility.api;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.persistence.PgDriverLocationIndex;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(results).extracting(n -> n.fix().driverId())
                    .containsExactly(nearest.driverId(), farther.driverId());
            assertThat(results).extracting(DriverLocationIndex.Nearby::distanceMeters).isSorted();
        });
        asserter.assertThat(() -> index.nearby(tenantA, CENTER, 500.0, VehicleType.CAR, NOW, 1), results ->
                assertThat(results).extracting(n -> n.fix().driverId()).containsExactly(nearest.driverId()));
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
                results -> assertThat(results).isEmpty());
        asserter.assertThat(() -> index.last(tenant, id), last -> assertThat(last).contains(moved));
        asserter.execute(() -> index.remove(tenant, id));
        asserter.assertThat(() -> index.last(tenant, id), last -> assertThat(last).isEmpty());
        asserter.assertThat(() -> index.nearby(tenant, moved.at(), 500.0, VehicleType.CAR, NOW, 10),
                results -> assertThat(results).isEmpty());
    }

    @Test
    @RunOnVertxContext
    void availabilityMutationIsTenantScopedAndDoesNotChangeCoordinates(UniAsserter asserter) {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var fix = fix("00000000-0000-0000-0000-000000000131", 0.0005, 0.0, VehicleType.CAR, true, NOW);

        asserter.execute(() -> index.upsert(tenantA, fix));
        asserter.execute(() -> index.setAvailable(tenantB, fix.driverId(), false));
        asserter.assertThat(() -> index.last(tenantA, fix.driverId()), last ->
                assertThat(last).get().extracting(DriverLocationIndex.Fix::available).isEqualTo(true));
        asserter.execute(() -> index.setAvailable(tenantA, fix.driverId(), false));
        asserter.assertThat(() -> index.last(tenantA, fix.driverId()), last -> {
            assertThat(last).get().extracting(DriverLocationIndex.Fix::at).isEqualTo(fix.at());
            assertThat(last).get().extracting(DriverLocationIndex.Fix::available).isEqualTo(false);
        });
        asserter.assertThat(() -> index.nearby(tenantA, CENTER, 500.0, VehicleType.CAR, NOW, 10),
                results -> assertThat(results).isEmpty());
    }

    private static DriverLocationIndex.Fix fix(
            String id, double latitude, double longitude, VehicleType vehicle, boolean available, Instant recordedAt) {
        return new DriverLocationIndex.Fix(
                DriverId.of(id), vehicle, new LatLng(latitude, longitude), 0.0, 0.0, recordedAt, available);
    }
}
