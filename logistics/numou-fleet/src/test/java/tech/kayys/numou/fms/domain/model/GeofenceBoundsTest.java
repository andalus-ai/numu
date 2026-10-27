package tech.kayys.numou.fms.domain.model;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.fms.domain.identifier.GeofenceId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeofenceBoundsTest {

    @Test
    void calculatesMaximumBoundsWhenAllCoordinatesAreNegative() {
        Geofence geofence = Geofence.createPolygon(
                GeofenceId.generate(),
                "Southern and western polygon",
                List.of(
                        new Geofence.GeofencePoint(-8.7, -120.4, 0),
                        new Geofence.GeofencePoint(-7.2, -119.1, 1),
                        new Geofence.GeofencePoint(-8.1, -118.8, 2)
                ));

        assertEquals(-8.7, geofence.getMinLatitude(), 0.0);
        assertEquals(-7.2, geofence.getMaxLatitude(), 0.0);
        assertEquals(-120.4, geofence.getMinLongitude(), 0.0);
        assertEquals(-118.8, geofence.getMaxLongitude(), 0.0);
    }

    @Test
    void calculatesBoundsAcrossBothHemispheres() {
        Geofence geofence = Geofence.createPolygon(
                GeofenceId.generate(),
                "Cross-equator polygon",
                List.of(
                        new Geofence.GeofencePoint(-1.0, -2.0, 0),
                        new Geofence.GeofencePoint(1.0, 2.0, 1),
                        new Geofence.GeofencePoint(0.5, -1.0, 2)
                ));

        assertEquals(-1.0, geofence.getMinLatitude(), 0.0);
        assertEquals(1.0, geofence.getMaxLatitude(), 0.0);
        assertEquals(-2.0, geofence.getMinLongitude(), 0.0);
        assertEquals(2.0, geofence.getMaxLongitude(), 0.0);
    }
}
