package tech.kayys.numou.talent.application.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Versioned SHA-256 fingerprint for immutable Talent command inputs.
 *
 * <p>Callers pass normalized scalar values and immutable record/list values.
 * The algorithm version is part of the input so future canonicalization
 * changes can be introduced without silently changing existing receipts.</p>
 */
public final class TalentProfileCommandFingerprint {
    private static final String ALGORITHM_VERSION = "talent-command-v1";

    private TalentProfileCommandFingerprint() {
    }

    public static String of(String operation, Object... values) {
        Objects.requireNonNull(operation, "operation");
        StringBuilder canonical = new StringBuilder(ALGORITHM_VERSION)
                .append('|').append(operation.length()).append(':').append(operation);
        for (Object value : values) {
            String part = canonicalValue(value);
            canonical.append('|').append(part.length()).append(':').append(part);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String canonicalValue(Object value) {
        if (value == null) return "<null>";
        if (value instanceof Iterable<?> iterable) {
            StringBuilder result = new StringBuilder("[");
            for (Object item : iterable) {
                String part = canonicalValue(item);
                result.append(part.length()).append(':').append(part).append(';');
            }
            return result.append(']').toString();
        }
        if (value.getClass().isArray()) {
            throw new IllegalArgumentException("Array command values must be converted to immutable lists");
        }
        return value.getClass().getName() + ':' + value;
    }
}
