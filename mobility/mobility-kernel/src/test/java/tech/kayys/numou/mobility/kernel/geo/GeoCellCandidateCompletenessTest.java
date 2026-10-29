package tech.kayys.numou.mobility.kernel.geo;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Differential checks for the candidate stage: any generated point inside the spherical radius must
 * belong to a cell enumerated for that query. This intentionally tests candidate completeness, not
 * exact result filtering.
 */
class GeoCellCandidateCompletenessTest {

    @Test
    void randomizedDestinationsWithinRadiusAreNeverOmittedFromCandidateCells() {
        Random random = new Random(0x6E554D4FL);

        for (int sample = 0; sample < 300; sample++) {
            LatLng center = new LatLng(
                    -80.0 + random.nextDouble() * 160.0,
                    -180.0 + random.nextDouble() * 360.0);
            double radiusMeters = 1.0 + random.nextDouble() * 5_000.0;
            var candidates = GeoCell.around(center, radiusMeters);

            for (int destinationIndex = 0; destinationIndex < 8; destinationIndex++) {
                double distanceMeters = random.nextDouble() * radiusMeters;
                double bearingRadians = random.nextDouble() * 2.0 * Math.PI;
                LatLng destination = destination(center, distanceMeters, bearingRadians);

                assertThat(GeoMath.haversineMeters(center, destination))
                        .as("generated destination must be inside the requested radius (sample %s)", sample)
                        .isLessThanOrEqualTo(radiusMeters + 1e-6);
                assertThat(candidates)
                        .as("candidate cells must include in-radius point %s for sample %s", destinationIndex, sample)
                        .contains(GeoCell.of(destination));
            }
        }
    }

    @Test
    void candidateCellsCoverBothRepresentationsOfTheAntimeridian() {
        for (double longitude : new double[] {-180.0, 180.0, -179.999, 179.999}) {
            LatLng center = new LatLng(0.0, longitude);
            var candidates = GeoCell.around(center, 500.0);

            assertThat(candidates).contains(GeoCell.of(new LatLng(0.0, -179.999)));
            assertThat(candidates).contains(GeoCell.of(new LatLng(0.0, 179.999)));
        }
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
