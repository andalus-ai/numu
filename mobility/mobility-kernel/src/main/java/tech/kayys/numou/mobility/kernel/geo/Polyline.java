package tech.kayys.numou.mobility.kernel.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Google's encoded polyline algorithm (precision 5, ~1 m): a GPS trace of thousands of points becomes a short ASCII string.
 * That is how a trip's route is stored as evidence and sent to maps; widely supported by every mapping SDK.
 */
public final class Polyline {

    private Polyline() { }

    public static String encode(List<LatLng> points) {
        final var sb = new StringBuilder();
        long prevLat = 0, prevLng = 0;
        for (final LatLng p : points) {
            final long lat = Math.round(p.lat() * 1e5), lng = Math.round(p.lng() * 1e5);
            write(lat - prevLat, sb);
            write(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    public static List<LatLng> decode(String encoded) {
        final var out = new ArrayList<LatLng>();
        int i = 0;
        long lat = 0, lng = 0;
        while (i < encoded.length()) {
            long[] r = read(encoded, i);
            lat += r[0];
            i = (int) r[1];
            r = read(encoded, i);
            lng += r[0];
            i = (int) r[1];
            out.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return out;
    }

    private static void write(long delta, StringBuilder sb) {
        long v = delta < 0 ? ~(delta << 1) : delta << 1;
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }

    private static long[] read(String s, int start) {
        long result = 0;
        int shift = 0, i = start;
        int b;
        do {
            b = s.charAt(i++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        return new long[]{(result & 1) != 0 ? ~(result >> 1) : result >> 1, i};
    }
}
