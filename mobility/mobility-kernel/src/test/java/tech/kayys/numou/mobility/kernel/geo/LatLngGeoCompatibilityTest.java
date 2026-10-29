package tech.kayys.numou.mobility.kernel.geo;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.geo.domain.GeoDistance;
import tech.kayys.numou.geo.domain.GeoGrid;
import tech.kayys.numou.geo.domain.GeoPoint;

import java.util.List;

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
    void sharedDistanceMatchesGeoDomainForCityAndAntimeridianCases() {
        LatLng jakarta = new LatLng(-6.2088, 106.8456);
        LatLng bandung = new LatLng(-6.9175, 107.6191);
        LatLng nearEastDateline = new LatLng(10, 179.9);
        LatLng nearWestDateline = new LatLng(10, -179.9);

        assertThat(GeoMath.haversineMeters(jakarta, bandung))
                .isEqualTo(GeoDistance.metersBetween(jakarta.toGeoPoint(), bandung.toGeoPoint()));
        assertThat(GeoMath.haversineMeters(nearEastDateline, nearWestDateline))
                .isEqualTo(GeoDistance.metersBetween(nearEastDateline.toGeoPoint(), nearWestDateline.toGeoPoint()));
    }


    @Test
    void candidateCellsWrapAcrossTheAntimeridianWithoutChangingLegacyKeys() {
        LatLng center = new LatLng(0, 179.999);
        LatLng acrossDateline = new LatLng(0, -179.999);

        assertThat(GeoCell.around(center, 500))
                .contains(GeoCell.of(acrossDateline))
                .contains(new GeoCell(0, 18_000))
                .contains(new GeoCell(0, -18_000));
    }

    @Test
    void candidateCellsCoverEveryLongitudeWhenSearchReachesPole() {
        LatLng center = new LatLng(89.999, 0);
        LatLng sameLatitudeOppositeLongitude = new LatLng(89.999, 180);

        assertThat(GeoDistance.metersBetween(center.toGeoPoint(), sameLatitudeOppositeLongitude.toGeoPoint()))
                .isLessThan(500);
        assertThat(GeoCell.around(center, 500)).contains(GeoCell.of(sameLatitudeOppositeLongitude));
    }

    @Test
    void candidateCellEnumerationRejectsInvalidRadii() {
        LatLng center = new LatLng(0, 0);
        assertThatIllegalArgumentException().isThrownBy(() -> GeoCell.around(center, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoCell.around(center, Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoCell.around(center, Double.POSITIVE_INFINITY));
    }

    @Test
    void pathLengthContinuesToSumGreatCircleSegments() {
        LatLng a = new LatLng(0, 0);
        LatLng b = new LatLng(0, 1);
        LatLng c = new LatLng(1, 1);

        assertThat(GeoMath.pathLengthMeters(List.of())).isZero();
        assertThat(GeoMath.pathLengthMeters(List.of(a))).isZero();
        assertThat(GeoMath.pathLengthMeters(List.of(a, b, c)))
                .isEqualTo(GeoMath.haversineMeters(a, b) + GeoMath.haversineMeters(b, c));
    }

    @Test
    void mobilityGridRetainsItsHistoricalIndexesAndKey() {
        LatLng jakarta = new LatLng(-6.2088, 106.8456);
        var legacyCell = GeoCell.of(jakarta);
        var sharedCell = GeoGrid.cellOf(jakarta.toGeoPoint(), GeoCell.CELL_DEGREES);

        assertThat(legacyCell.latIndex()).isEqualTo(sharedCell.latitudeIndex());
        assertThat(legacyCell.lngIndex()).isEqualTo(sharedCell.longitudeIndex());
        assertThat(legacyCell.key()).isEqualTo("-621:10684");
    }

    @Test
    void legacyPolylineFacadeUsesSharedPrecisionFiveCodec() {
        List<LatLng> path = List.of(
                new LatLng(38.5, -120.2),
                new LatLng(40.7, -120.95),
                new LatLng(43.252, -126.453));

        String encoded = Polyline.encode(path);

        assertThat(encoded).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        List<LatLng> decoded = Polyline.decode(encoded);
        assertThat(decoded).containsExactlyElementsOf(path);
        decoded.add(new LatLng(0, 0)); // Preserve the legacy facade's mutable-list behavior.
        assertThat(decoded).hasSize(4);
    }

    @Test
    void preservesRoundedLogRepresentation() {
        assertThat(new LatLng(-6.208812345, 106.845612345).toString())
                .isEqualTo("LatLng[~-6.21,106.85]");
    }
}
