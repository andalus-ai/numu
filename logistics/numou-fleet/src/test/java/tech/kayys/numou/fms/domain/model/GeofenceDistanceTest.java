package tech.kayys.numou.fms.domain.model;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.fms.domain.identifier.GeofenceId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeofenceDistanceTest {

    @Test
    void circleMembershipUsesSharedGreatCircleDistanceAndPreservesKilometreRadius() {
        Geofence withinSixKilometres = Geofence.create(
                GeofenceId.generate(), "Nearby", "CIRCLE", 0.0, 0.0, 6.0);
        Geofence withinFiveKilometres = Geofence.create(
                GeofenceId.generate(), "Closer", "CIRCLE", 0.0, 0.0, 5.0);

        // 0.05 degrees latitude is approximately 5.56 km from the equator.
        assertTrue(withinSixKilometres.isInside(0.05, 0.0));
        assertFalse(withinFiveKilometres.isInside(0.05, 0.0));
    }

    @Test
    void circleMembershipHandlesAntimeridianProximity() {
        Geofence geofence = Geofence.create(
                GeofenceId.generate(), "Dateline", "CIRCLE", 0.0, 179.99, 3.0);

        assertTrue(geofence.isInside(0.0, -179.99));
    }
}
