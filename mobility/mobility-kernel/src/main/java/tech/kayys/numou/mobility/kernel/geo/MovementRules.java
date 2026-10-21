package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.time.Duration;
import java.time.Instant;

/** Decides whether a GPS fix is believable relative to the previous one (rejects teleports and fake-GPS jumps). */
public final class MovementRules {

    /** Fixes closer than this are treated as the same position (GPS jitter). */
    public static final double JITTER_METERS = 8.0;

    private MovementRules() { }

    public static boolean isPlausible(LatLng from, Instant fromAt, LatLng to, Instant toAt, VehicleType vehicle) {
        final double seconds = Duration.between(fromAt, toAt).toMillis() / 1000.0;
        if (seconds <= 0) {
            return false; // out-of-order or duplicate timestamp
        }
        final double meters = from.distanceTo(to);
        if (meters < JITTER_METERS) {
            return true;
        }
        return GeoMath.kmh(meters, seconds) <= vehicle.maxPlausibleKmh();
    }
}
