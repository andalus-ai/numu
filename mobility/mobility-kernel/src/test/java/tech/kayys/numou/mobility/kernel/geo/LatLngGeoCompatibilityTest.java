package tech.kayys.numou.mobility.kernel.geo;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.geo.domain.GeoPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LatLngGeoCompatibilityTest {

    @Test
    void roundTripsCoordinatesWithoutPrecisionLoss() {
        LatLng mobility = new LatLng(-6.208812345678, 106.845612345678);

        assertThat(mobility.toGeoPoint())
                .isEqualTo(new GeoPoint(-6.208812345678, 106.845612345678));
        assertThat(LatLng.fromGeoPoint(mobility.toGeoPoint())).isEqualTo(mobility);
    }

    @Test
    void acceptsGeographicEdges() {
        assertThat(new LatLng(-90, -180).toGeoPoint()).isEqualTo(new GeoPoint(-90, -180));
        assertThat(LatLng.fromGeoPoint(new GeoPoint(90, 180))).isEqualTo(new LatLng(90, 180));
    }

    @Test
    void rejectsNonFiniteCoordinatesAndNullConversionInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LatLng(Double.NaN, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new LatLng(0, Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> LatLng.fromGeoPoint(null));
    }

    @Test
    void preservesRoundedLogRepresentation() {
        assertThat(new LatLng(-6.208812345, 106.845612345).toString())
                .isEqualTo("LatLng[~-6.21,106.85]");
    }
}
