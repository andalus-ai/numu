package tech.kayys.numou.geo.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GeoGridTest {

    @Test
    void mapsCoordinatesToFloorBasedCellsIncludingNegativeCoordinates() {
        GeoGridCell jakarta = GeoGrid.cellOf(new GeoPoint(-6.2088, 106.8456), 0.01);
        assertThat(jakarta.latitudeIndex()).isEqualTo(-621);
        assertThat(jakarta.longitudeIndex()).isEqualTo(10_684);
        assertThat(jakarta.key()).isEqualTo("0.01:-621:10684");
    }

    @Test
    void gridResolutionIsPartOfCellIdentity() {
        GeoPoint point = new GeoPoint(12.345, 67.89);

        assertThat(GeoGrid.cellOf(point, 0.1)).isNotEqualTo(GeoGrid.cellOf(point, 0.01));
        assertThat(GeoGrid.cellOf(point, 0.1).key()).startsWith("0.1:");
        assertThat(GeoGrid.cellOf(point, 0.01).key()).startsWith("0.01:");
    }

    @Test
    void handlesCoordinateEdgesWithoutWrappingLongitude() {
        assertThat(GeoGrid.cellOf(new GeoPoint(-90, -180), 1).latitudeIndex()).isEqualTo(-90);
        assertThat(GeoGrid.cellOf(new GeoPoint(90, 180), 1).longitudeIndex()).isEqualTo(180);
        assertThat(GeoGrid.cellOf(new GeoPoint(0, -180), 1).longitudeIndex())
                .isNotEqualTo(GeoGrid.cellOf(new GeoPoint(0, 180), 1).longitudeIndex());
    }

    @Test
    void rejectsInvalidResolutionAndNullPoint() {
        assertThatNullPointerException().isThrownBy(() -> GeoGrid.cellOf(null, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoGrid.cellOf(new GeoPoint(0, 0), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoGrid.cellOf(new GeoPoint(0, 0), Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoGrid.cellOf(new GeoPoint(0, 0), 361));
        assertThatIllegalArgumentException().isThrownBy(
                () -> GeoGrid.cellOf(new GeoPoint(90, 180), Double.MIN_VALUE));
    }
}
