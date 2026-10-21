package tech.kayys.numou.mobility.ride.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The pickup PIN proves the person getting in is the rider who booked (prevents getting in the wrong car, and a driver
 * "starting" trips without a rider). Only a salted hash is stored; comparison is constant-time.
 */
public final class PinHasher {

    private PinHasher() { }

    public static String hash(UUID rideId, String pin) {
        try {
            final var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((rideId + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(UUID rideId, String pin, String storedHash) {
        if (pin == null || storedHash == null) return false;
        return MessageDigest.isEqual(hash(rideId, pin).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
