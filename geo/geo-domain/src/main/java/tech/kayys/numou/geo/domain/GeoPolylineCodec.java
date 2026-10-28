package tech.kayys.numou.geo.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral codec for Google's encoded polyline format at precision 5.
 *
 * <p>The codec preserves the format's quantization (roughly metre-scale, latitude-dependent
 * precision); it is a compact transport representation, not a lossless coordinate format.
 * Routing providers and consumers may use other encodings without changing Geo's domain model.</p>
 */
public final class GeoPolylineCodec {

    private static final double SCALE = 100_000.0;

    private GeoPolylineCodec() {
    }

    /** Encodes coordinates as a precision-5 polyline. */
    public static String encode(List<GeoPoint> points) {
        Objects.requireNonNull(points, "points");
        StringBuilder encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;

        for (GeoPoint point : points) {
            Objects.requireNonNull(point, "path point");
            long latitude = Math.round(point.latitude() * SCALE);
            long longitude = Math.round(point.longitude() * SCALE);
            writeDelta(latitude - previousLatitude, encoded);
            writeDelta(longitude - previousLongitude, encoded);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    /** Decodes a precision-5 polyline and rejects malformed or out-of-range coordinates. */
    public static List<GeoPoint> decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        long latitude = 0;
        long longitude = 0;

        while (index < encoded.length()) {
            DecodedComponent decodedLatitude = readDelta(encoded, index);
            index = decodedLatitude.nextIndex();
            if (index >= encoded.length()) {
                throw new IllegalArgumentException("polyline has an incomplete coordinate pair");
            }
            DecodedComponent decodedLongitude = readDelta(encoded, index);
            index = decodedLongitude.nextIndex();

            try {
                latitude = Math.addExact(latitude, decodedLatitude.delta());
                longitude = Math.addExact(longitude, decodedLongitude.delta());
                points.add(new GeoPoint(latitude / SCALE, longitude / SCALE));
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("polyline coordinate delta overflows", exception);
            }
        }
        return List.copyOf(points);
    }

    private static void writeDelta(long delta, StringBuilder encoded) {
        long value = delta < 0 ? ~(delta << 1) : delta << 1;
        while (value >= 0x20) {
            encoded.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>>= 5;
        }
        encoded.append((char) (value + 63));
    }

    private static DecodedComponent readDelta(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        while (true) {
            if (index >= encoded.length()) {
                throw new IllegalArgumentException("polyline ends inside a coordinate delta");
            }
            int value = encoded.charAt(index++) - 63;
            if (value < 0 || value > 63) {
                throw new IllegalArgumentException("polyline contains an invalid character");
            }
            int payload = value & 0x1f;
            if (shift >= 63 || (shift == 60 && payload > 7)) {
                throw new IllegalArgumentException("polyline coordinate delta is too large");
            }
            result |= (long) payload << shift;
            if (value < 0x20) {
                long delta = (result & 1L) == 1L ? ~(result >>> 1) : result >>> 1;
                return new DecodedComponent(delta, index);
            }
            shift += 5;
        }
    }

    private record DecodedComponent(long delta, int nextIndex) {
    }
}
