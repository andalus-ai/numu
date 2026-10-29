package tech.kayys.numou.mobility.driver.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDriverLocationIndexContractTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LatLng CENTER = new LatLng(0.0, 0.0);

    private final InMemoryDriverLocationIndex index = new InMemoryDriverLocationIndex();

    @Test
    void nearbyAppliesExactRadiusVehicleAvailabilityFreshnessTenantAndLimit() {
        var nearest = fix("00000000-0000-0000-0000-000000000011", 0.0005, 0.0, VehicleType.CAR, true, NOW);
        var farther = fix("00000000-0000-0000-0000-000000000012", 0.0010, 0.0, VehicleType.CAR, true, NOW);
        var outsideRadiusButInCandidateGrid = fix("00000000-0000-0000-0000-000000000013", 0.0049, 0.0, VehicleType.CAR, true, NOW);
        var unavailable = fix("00000000-0000-0000-0000-000000000014", 0.0002, 0.0, VehicleType.CAR, false, NOW);
        var wrongVehicle = fix("00000000-0000-0000-0000-000000000015", 0.0001, 0.0, VehicleType.BIKE, true, NOW);
        var stale = fix("00000000-0000-0000-0000-000000000016", 0.0001, 0.0, VehicleType.CAR, true, NOW.minusSeconds(1));
        var otherTenant = fix("00000000-0000-0000-0000-000000000017", 0.0001, 0.0, VehicleType.CAR, true, NOW);

        for (var f : new DriverLocationIndex.Fix[] {nearest, farther, outsideRadiusButInCandidateGrid, unavailable, wrongVehicle, stale}) {
            index.upsert(TENANT_A, f).await().indefinitely();
        }
        index.upsert(TENANT_B, otherTenant).await().indefinitely();

        var results = index.nearby(TENANT_A, CENTER, 500.0, VehicleType.CAR, NOW, 10).await().indefinitely();

        assertThat(results).extracting(n -> n.fix().driverId())
                .containsExactly(nearest.driverId(), farther.driverId());
        assertThat(results).extracting(DriverLocationIndex.Nearby::distanceMeters)
                .isSorted();
        assertThat(index.nearby(TENANT_A, CENTER, 500.0, VehicleType.CAR, NOW, 1).await().indefinitely())
                .extracting(n -> n.fix().driverId())
                .containsExactly(nearest.driverId());
    }

    @Test
    void upsertReindexesMovedDriverAndRemoveClearsSearchResults() {
        var id = DriverId.of("00000000-0000-0000-0000-000000000021");
        var original = new DriverLocationIndex.Fix(id, VehicleType.CAR, new LatLng(0.0005, 0.0),
                0.0, 0.0, NOW, true);
        var moved = new DriverLocationIndex.Fix(id, VehicleType.CAR, new LatLng(0.0200, 0.0),
                0.0, 0.0, NOW.plusSeconds(5), true);

        index.upsert(TENANT_A, original).await().indefinitely();
        index.upsert(TENANT_A, moved).await().indefinitely();

        assertThat(index.nearby(TENANT_A, CENTER, 500.0, VehicleType.CAR, NOW, 10).await().indefinitely())
                .isEmpty();
        assertThat(index.last(TENANT_A, id).await().indefinitely()).contains(moved);

        index.remove(TENANT_A, id).await().indefinitely();
        assertThat(index.last(TENANT_A, id).await().indefinitely()).isEmpty();
        assertThat(index.nearby(TENANT_A, moved.at(), 500.0, VehicleType.CAR, NOW, 10).await().indefinitely())
                .isEmpty();
    }

    @Test
    void availabilityChangesAreTenantScopedAndDoNotMoveTheFix() {
        var fix = fix("00000000-0000-0000-0000-000000000031", 0.0005, 0.0, VehicleType.CAR, true, NOW);
        index.upsert(TENANT_A, fix).await().indefinitely();

        index.setAvailable(TENANT_B, fix.driverId(), false).await().indefinitely();
        assertThat(index.last(TENANT_A, fix.driverId()).await().indefinitely())
                .get()
                .extracting(DriverLocationIndex.Fix::available)
                .isEqualTo(true);

        index.setAvailable(TENANT_A, fix.driverId(), false).await().indefinitely();
        assertThat(index.last(TENANT_A, fix.driverId()).await().indefinitely())
                .get()
                .extracting(DriverLocationIndex.Fix::at)
                .isEqualTo(fix.at());
        assertThat(index.nearby(TENANT_A, CENTER, 500.0, VehicleType.CAR, NOW, 10).await().indefinitely())
                .isEmpty();
    }

    private static DriverLocationIndex.Fix fix(
            String id, double latitude, double longitude, VehicleType vehicle, boolean available, Instant recordedAt) {
        return new DriverLocationIndex.Fix(
                DriverId.of(id), vehicle, new LatLng(latitude, longitude), 0.0, 0.0, recordedAt, available);
    }
}
