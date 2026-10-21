package tech.kayys.numou.mobility.kernel;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.kernel.geo.GeoMath;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.PathSimplifier;
import tech.kayys.numou.mobility.kernel.geo.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineAndPathTest {

    static final LatLng MONAS = new LatLng(-6.1754, 106.8272);

    @Test
    void encodesTheReferenceExampleFromTheAlgorithmSpecification() {
        final var pts = List.of(new LatLng(38.5, -120.2), new LatLng(40.7, -120.95), new LatLng(43.252, -126.453));
        assertThat(Polyline.encode(pts)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")).isEqualTo(pts);
    }

    @Test
    void roundTripsRandomTracesToAboutOneMetre() {
        final var rnd = new Random(5);
        final List<LatLng> trace = new ArrayList<>();
        LatLng p = MONAS;
        for (int i = 0; i < 500; i++) { p = GeoMath.offset(p, rnd.nextDouble() * 60 - 20, rnd.nextDouble() * 60 - 30); trace.add(p); }
        final var back = Polyline.decode(Polyline.encode(trace));
        assertThat(back).hasSameSizeAs(trace);
        for (int i = 0; i < trace.size(); i++) assertThat(trace.get(i).distanceTo(back.get(i))).isLessThan(1.5);
        assertThat(Polyline.encode(trace).length()).isLessThan(500 * 12);               // a few bytes per point, not a JSON object each
    }

    @Test
    void emptyAndSinglePointTracesWork() {
        assertThat(Polyline.encode(List.of())).isEmpty();
        assertThat(Polyline.decode("")).isEmpty();
        assertThat(Polyline.decode(Polyline.encode(List.of(MONAS))).get(0).distanceTo(MONAS)).isLessThan(1.5);
    }

    @Test
    void distanceToSegmentCoversPerpendicularEndpointAndDegenerateCases() {
        final var a = MONAS; final var b = GeoMath.offset(MONAS, 0, 2_000);       // 2 km due east
        assertThat(GeoMath.distanceToSegmentMeters(GeoMath.offset(MONAS, 500, 1_000), a, b)).isBetween(495.0, 505.0);   // beside the middle
        assertThat(GeoMath.distanceToSegmentMeters(GeoMath.offset(MONAS, 0, 3_000), a, b)).isBetween(995.0, 1_005.0);   // beyond the end: distance to b
        assertThat(GeoMath.distanceToSegmentMeters(GeoMath.offset(MONAS, 0, -400), a, b)).isBetween(395.0, 405.0);      // before the start
        assertThat(GeoMath.distanceToSegmentMeters(GeoMath.offset(MONAS, 300, 400), a, a)).isBetween(495.0, 505.0);     // zero-length segment
        assertThat(GeoMath.distanceToSegmentMeters(GeoMath.offset(MONAS, 0, 700), a, b)).isLessThan(1.0);                // on the line
    }

    @Test
    void simplifyingKeepsTheShapeAndShrinksTheTrace() {
        final List<LatLng> straight = new ArrayList<>();
        for (int i = 0; i <= 100; i++) straight.add(GeoMath.offset(MONAS, i * 20, 0));
        assertThat(PathSimplifier.simplify(straight, 5)).hasSize(2);                    // a straight road is just its two ends

        final List<LatLng> ell = new ArrayList<>();
        for (int i = 0; i <= 50; i++) ell.add(GeoMath.offset(MONAS, i * 20, 0));
        for (int i = 1; i <= 50; i++) ell.add(GeoMath.offset(MONAS, 1_000, i * 20));
        final var simple = PathSimplifier.simplify(ell, 5);
        assertThat(simple).hasSize(3);
        assertThat(simple.get(1).distanceTo(GeoMath.offset(MONAS, 1_000, 0))).isLessThan(1.0);   // the corner survives
        assertThat(simple.get(0)).isEqualTo(ell.get(0));
        assertThat(simple.get(2)).isEqualTo(ell.get(ell.size() - 1));
        assertThat(PathSimplifier.simplify(List.of(MONAS), 5)).hasSize(1);
    }
}
