package tech.kayys.numou.mobility.kernel.geo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Douglas-Peucker: drops points that deviate less than the tolerance from the line through their neighbours. Always keeps both end points. */
public final class PathSimplifier {

    private PathSimplifier() { }

    public static List<LatLng> simplify(List<LatLng> path, double toleranceMeters) {
        if (path.size() < 3) return List.copyOf(path);
        final boolean[] keep = new boolean[path.size()];
        keep[0] = true;
        keep[path.size() - 1] = true;
        final var stack = new ArrayDeque<int[]>();
        stack.push(new int[]{0, path.size() - 1});
        while (!stack.isEmpty()) {
            final int[] seg = stack.pop();
            double max = 0;
            int idx = -1;
            for (int i = seg[0] + 1; i < seg[1]; i++) {
                final double d = GeoMath.distanceToSegmentMeters(path.get(i), path.get(seg[0]), path.get(seg[1]));
                if (d > max) {
                    max = d;
                    idx = i;
                }
            }
            if (idx >= 0 && max > toleranceMeters) {
                keep[idx] = true;
                stack.push(new int[]{seg[0], idx});
                stack.push(new int[]{idx, seg[1]});
            }
        }
        final var out = new ArrayList<LatLng>();
        for (int i = 0; i < keep.length; i++) if (keep[i]) out.add(path.get(i));
        return out;
    }
}
