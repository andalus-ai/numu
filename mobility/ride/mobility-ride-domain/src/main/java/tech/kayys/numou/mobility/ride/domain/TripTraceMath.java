package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.geo.MovementRules;

import java.util.List;

/**
 * Distance travelled according to a stored trace: the same rules as live tracking (implausible jumps and out-of-order fixes are skipped,
 * jitter below {@link MovementRules#JITTER_METERS} is not counted). Computing it once at the end, from an append-only trace, replaces
 * rewriting the whole ride document on every GPS fix.
 */
public final class TripTraceMath {

    private TripTraceMath() { }

    public static double trackedDistanceMeters(List<TracePoint> points, VehicleType vehicle) {
        if (points.size() < 2) return 0;
        double total = 0;
        TracePoint last = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            final TracePoint p = points.get(i);
            if (!MovementRules.isPlausible(last.at(), last.time(), p.at(), p.time(), vehicle)) continue;
            final double d = last.at().distanceTo(p.at());
            if (d >= MovementRules.JITTER_METERS) {
                total += d;
                last = p;
            }
        }
        return total;
    }

    public static List<LatLng> path(List<TracePoint> points) {
        return points.stream().map(TracePoint::at).toList();
    }
}
