package tech.kayys.numou.geo.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GeoDomainTest {

    @Test
    void acceptsValidWgs84EdgesAndRejectsNonFiniteOrOutOfRangeValues() {
        assertThat(new GeoPoint(-90, -180).latitude()).isEqualTo(-90);
        assertThat(new GeoPoint(90, 180).longitude()).isEqualTo(180);
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(Double.NaN, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(0, Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(90.001, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new GeoPoint(0, -180.001));
    }

    @Test
    void computesZeroAndKnownApproximateGreatCircleDistances() {
        GeoPoint jakarta = new GeoPoint(-6.2088, 106.8456);
        assertThat(GeoDistance.metersBetween(jakarta, jakarta)).isZero();
        GeoPoint bandung = new GeoPoint(-6.9175, 107.6191);
        assertThat(GeoDistance.metersBetween(jakarta, bandung)).isBetween(110_000.0, 130_000.0);
    }

    @Test
    void computesStableDistanceForAntipodalPoints() {
        double halfCircumference = Math.PI * GeoDistance.EARTH_RADIUS_METERS;
        assertThat(GeoDistance.metersBetween(new GeoPoint(0, 0), new GeoPoint(0, 180)))
                .isCloseTo(halfCircumference, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void rejectsNullPointsEvenForSinglePointPaths() {
        assertThatNullPointerException().isThrownBy(() -> GeoDistance.pathLengthMeters(Arrays.asList((GeoPoint) null)));
        assertThatNullPointerException().isThrownBy(() -> GeoDistance.pathLengthMeters(
                Arrays.asList(new GeoPoint(0, 0), null)));
    }

    @Test
    void handlesAntimeridianBounds() {
        GeoBounds bounds = new GeoBounds(-20, 170, 20, -170);
        assertThat(bounds.contains(new GeoPoint(0, 179))).isTrue();
        assertThat(bounds.contains(new GeoPoint(0, -179))).isTrue();
        assertThat(bounds.contains(new GeoPoint(0, 0))).isFalse();
        assertThat(bounds.contains(new GeoPoint(21, 179))).isFalse();
    }

    @Test
    void sumsPathSegmentsAndTreatsShortPathsAsZero() {
        GeoPoint a = new GeoPoint(0, 0);
        GeoPoint b = new GeoPoint(0, 1);
        assertThat(GeoDistance.pathLengthMeters(List.of())).isZero();
        assertThat(GeoDistance.pathLengthMeters(List.of(a))).isZero();
        assertThat(GeoDistance.pathLengthMeters(List.of(a, b)))
                .isEqualTo(GeoDistance.metersBetween(a, b));
    }

    @Test
    void coordinateStringDoesNotExposeFullPrecision() {
        assertThat(new GeoPoint(-6.208812345, 106.845612345).toString())
                .isEqualTo("GeoPoint[~-6.21,~106.85]");
    }
}
