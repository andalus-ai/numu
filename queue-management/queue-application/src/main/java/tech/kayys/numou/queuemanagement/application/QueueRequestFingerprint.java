package tech.kayys.numou.queuemanagement.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable SHA-256 fingerprint for normalized command arguments.
 *
 * Each UTF-8 component is length-prefixed so values containing separators cannot
 * produce ambiguous fingerprints. Callers must pass semantic command fields only;
 * actor, correlation ID, idempotency key, and wall-clock timestamps are intentionally
 * not implicit inputs.
 */
public final class QueueRequestFingerprint {
    private QueueRequestFingerprint() {
    }

    public static String sha256(String... components) {
        if (components == null) {
            throw new IllegalArgumentException("components must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String component : components) {
                if (component == null) {
                    throw new IllegalArgumentException("fingerprint components must not be null");
                }
                byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
