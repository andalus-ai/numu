package tech.kayys.numou.construction.application.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 fingerprint of an already-canonicalized request representation. */
public final class RequestFingerprint {
    private RequestFingerprint() {}

    /**
     * Hashes UTF-8 bytes exactly as supplied. Callers must canonicalize structured
     * payloads first (stable property order, normalized numbers, and explicit defaults).
     */
    public static String sha256(String canonicalRequest) {
        Objects.requireNonNull(canonicalRequest, "canonicalRequest");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }
}
