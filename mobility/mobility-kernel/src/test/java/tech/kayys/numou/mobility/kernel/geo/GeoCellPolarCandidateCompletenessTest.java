package tech.kayys.numou.mobility.kernel.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GeoCellPolarCandidateCompletenessTest {

    @Test
    void includesGeneratedInRadiusPointsWhenSearchReachesEitherPole() {
        for (double latitude : new double[] {89.999, -89.999}) {
            LatLng center = new LatLng(latitude, 37.0);
            double radiusMeters = 500.0;
            var candidates = GeoCell.around(center, radiusMeters);

            for (int bearing = 0; bearing < 360; bearing += 15) {
                LatLng destination = destination(center, radiusMeters * 0.9, Math.toRadians(bearing));

                assertThat(GeoMath.haversineMeters(center, destination))
                        .as("generated point must remain within radius at latitude %s and bearing %s", latitude, bearing)
                        .isLessThanOrEqualTo(radiusMeters + 1e-6);
                assertThat(candidates)
                        .as("candidate cells must cover latitude %s and bearing %s", latitude, bearing)
                        .contains(GeoCell.of(destination));
            }
        }
    }

    @Test
    void rejectsSearchesWhoseCandidateEnumerationExceedsSafetyLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoCell.around(new LatLng(0.0, 0.0), 1_000_000.0))
                .withMessageContaining("too many candidate cells");
    }

    private static LatLng destination(LatLng origin, double distanceMeters, double bearingRadians) {
        double angularDistance = distanceMeters / GeoMath.EARTH_RADIUS_M;
        double latitude1 = Math.toRadians(origin.lat());
        double longitude1 = Math.toRadians(origin.lng());

        double sinLatitude2 = Math.sin(latitude1) * Math.cos(angularDistance)
                + Math.cos(latitude1) * Math.sin(angularDistance) * Math.cos(bearingRadians);
        double latitude2 = Math.asin(Math.max(-1.0, Math.min(1.0, sinLatitude2)));
        double longitude2 = longitude1 + Math.atan2(
                Math.sin(bearingRadians) * Math.sin(angularDistance) * Math.cos(latitude1),
                Math.cos(angularDistance) - Math.sin(latitude1) * Math.sin(latitude2));

        double longitudeDegrees = Math.toDegrees(longitude2);
        longitudeDegrees = ((longitudeDegrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return new LatLng(Math.toDegrees(latitude2), longitudeDegrees);
    }
}
