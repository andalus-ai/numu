package tech.kayys.numou.geo.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GeoPolylineCodecTest {

    @Test
    void roundTripsPrecisionFiveCoordinates() {
        List<GeoPoint> points = List.of(
                new GeoPoint(38.5, -120.2),
                new GeoPoint(40.7, -120.95),
                new GeoPoint(43.252, -126.453));

        String encoded = GeoPolylineCodec.encode(points);

        assertThat(encoded).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(GeoPolylineCodec.decode(encoded)).containsExactlyElementsOf(points);
    }

    @Test
    void handlesEmptyAndSinglePointPaths() {
        assertThat(GeoPolylineCodec.encode(List.of())).isEmpty();
        assertThat(GeoPolylineCodec.decode("")).isEmpty();

        GeoPoint point = new GeoPoint(-6.20881, 106.84561);
        assertThat(GeoPolylineCodec.decode(GeoPolylineCodec.encode(List.of(point))))
                .containsExactly(new GeoPoint(-6.20881, 106.84561));
    }

    @Test
    void rejectsNullInputAndNullPoints() {
        assertThatNullPointerException().isThrownBy(() -> GeoPolylineCodec.encode(null));
        assertThatNullPointerException().isThrownBy(() -> GeoPolylineCodec.decode(null));
        assertThatNullPointerException().isThrownBy(
                () -> GeoPolylineCodec.encode(java.util.Arrays.asList((GeoPoint) null)));
    }

    @Test
    void rejectsMalformedOrOutOfRangeEncodedCoordinates() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPolylineCodec.decode("_"));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPolylineCodec.decode("~~~~~~~"));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPolylineCodec.decode(String.valueOf((char) 0) + "?"));
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPolylineCodec.decode("_mljP?"));
    }
}
