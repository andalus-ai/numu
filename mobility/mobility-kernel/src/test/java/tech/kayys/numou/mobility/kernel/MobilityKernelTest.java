package tech.kayys.numou.mobility.kernel;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.*;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobilityKernelTest {

    static final LatLng MONAS = new LatLng(-6.1754, 106.8272);

    @Test
    void oneDegreeOfLongitudeAtTheEquatorIs111Km() {
        assertThat(new LatLng(0, 0).distanceTo(new LatLng(0, 1))).isBetween(111_190.0, 111_200.0);
        assertThat(new LatLng(0, 0).distanceTo(new LatLng(1, 0))).isBetween(111_190.0, 111_200.0);
    }

    @Test
    void distanceIsSymmetricAndZeroToSelf() {
        final var bandung = new LatLng(-6.9175, 107.6191);
        assertThat(MONAS.distanceTo(bandung)).isEqualTo(bandung.distanceTo(MONAS)).isBetween(115_000.0, 125_000.0);
        assertThat(MONAS.distanceTo(MONAS)).isZero();
    }

    @Test
    void offsetMovesByTheRequestedMetres() {
        final var moved = GeoMath.offset(MONAS, 1000, 0);
        assertThat(MONAS.distanceTo(moved)).isBetween(995.0, 1005.0);
        assertThat(MONAS.distanceTo(GeoMath.offset(MONAS, 300, 400))).isBetween(495.0, 505.0);
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThatThrownBy(() -> new LatLng(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LatLng(0, 181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LatLng(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locationsAreRoundedInLogs() {
        assertThat(new LatLng(-6.175392, 106.827153).toString()).isEqualTo("LatLng[~-6.18,106.83]");
        assertThat(new Place(MONAS, "Jl. Merdeka Barat No. 12, rumah Budi").toString()).doesNotContain("Budi", "Merdeka");
    }

    @Test
    void pathLengthSumsSegments() {
        final var a = MONAS; final var b = GeoMath.offset(a, 1000, 0); final var c = GeoMath.offset(b, 0, 500);
        assertThat(GeoMath.pathLengthMeters(List.of(a, b, c))).isBetween(1490.0, 1510.0);
        assertThat(GeoMath.pathLengthMeters(List.of(a))).isZero();
    }

    /** The search area must never miss a point that is genuinely inside the radius, at any latitude. */
    @Test
    void cellsAroundAlwaysCoverEveryPointInsideTheRadius() {
        final var rnd = new Random(42);
        for (final double lat : new double[]{-6.2, 0.0, 35.7, 60.0, -45.0}) {
            final var center = new LatLng(lat, 106.8);
            for (final double radius : new double[]{300, 1500, 5000}) {
                final var cells = GeoCell.around(center, radius);
                for (int i = 0; i < 3000; i++) {
                    final double ang = rnd.nextDouble() * 2 * Math.PI, dist = rnd.nextDouble() * radius;
                    final var p = GeoMath.offset(center, Math.cos(ang) * dist, Math.sin(ang) * dist);
                    if (center.distanceTo(p) <= radius) {
                        assertThat(cells).as("lat=%s r=%s", lat, radius).contains(GeoCell.of(p));
                    }
                }
            }
        }
    }

    @Test
    void cellSearchStaysSmall() {
        assertThat(GeoCell.around(MONAS, 1500).size()).isLessThanOrEqualTo(16);
        assertThat(GeoCell.of(MONAS).key()).isEqualTo(GeoCell.of(GeoMath.offset(MONAS, 5, 5)).key());
    }

    @Test
    void teleportsAndOutOfOrderFixesAreImplausible() {
        final var t0 = Instant.parse("2026-10-10T10:00:00Z");
        final var far = GeoMath.offset(MONAS, 5000, 0);
        assertThat(MovementRules.isPlausible(MONAS, t0, far, t0.plusSeconds(10), VehicleType.CAR)).isFalse();   // 1,800 km/h
        assertThat(MovementRules.isPlausible(MONAS, t0, far, t0.plusSeconds(300), VehicleType.CAR)).isTrue();   // 60 km/h
        assertThat(MovementRules.isPlausible(MONAS, t0, far, t0.minusSeconds(5), VehicleType.CAR)).isFalse();   // time went backwards
        assertThat(MovementRules.isPlausible(MONAS, t0, GeoMath.offset(MONAS, 3, 3), t0.plusSeconds(1), VehicleType.BIKE)).isTrue(); // jitter
    }

    @Test
    void fallbackRoutingIsConservativeAndVehicleAware() {
        final var to = GeoMath.offset(MONAS, 5000, 0);
        final var car = new HaversineRouting().route(MONAS, to, VehicleType.CAR).await().indefinitely();
        final var bike = new HaversineRouting().route(MONAS, to, VehicleType.BIKE).await().indefinitely();
        assertThat(car.distanceMeters()).isBetween(6700.0, 6800.0);       // 5 km x 1.35 detour
        assertThat(bike.durationSeconds()).isLessThan(car.durationSeconds());
    }
}
