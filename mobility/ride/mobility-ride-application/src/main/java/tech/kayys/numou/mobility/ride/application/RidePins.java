package tech.kayys.numou.mobility.ride.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * The 4-digit pickup PIN, derived as HMAC(secret, rideId). Stateless: the rider app can ask for it again at any time without
 * the server storing it in clear (only its hash is on the ride), and a driver cannot predict it without the secret.
 */
public final class RidePins {

    private final byte[] secret;

    public RidePins(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("PIN secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String pinFor(UUID rideId) {
        try {
            final var m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            final int n = ByteBuffer.wrap(m.doFinal(("pin:" + rideId).getBytes(StandardCharsets.UTF_8))).getInt() & 0x7fffffff;
            return String.format("%04d", n % 10_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
