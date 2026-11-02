package tech.kayys.numou.courier.application.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies partner webhook signatures over the exact received bytes.
 *
 * Header format: t=<unix-seconds>,v1=<lowercase-hex-hmac-sha256>
 * Signed message: ASCII decimal timestamp + "." + exact raw request body.
 */
public final class PartnerWebhookSignatureVerifier {
    private static final String HMAC = "HmacSHA256";
    private static final Duration MAX_TOLERANCE = Duration.ofMinutes(10);

    private PartnerWebhookSignatureVerifier() { }

    public static void verify(byte[] rawBody, String signatureHeader, byte[] secret,
                              Instant now, Duration tolerance) {
        Objects.requireNonNull(rawBody, "rawBody");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(tolerance, "tolerance");
        if (rawBody.length == 0 || rawBody.length > 1_048_576)
            throw new SecurityException("webhook body size is outside the accepted range");
        if (secret.length < 32) throw new SecurityException("webhook signing key is too short");
        if (tolerance.isNegative() || tolerance.isZero() || tolerance.compareTo(MAX_TOLERANCE) > 0)
            throw new IllegalArgumentException("signature tolerance must be between 1 ms and 10 minutes");

        ParsedHeader parsed = parse(signatureHeader);
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(parsed.timestamp());
        } catch (RuntimeException invalid) {
            throw new SecurityException("webhook signature timestamp is invalid", invalid);
        }
        Duration age = Duration.between(signedAt, now).abs();
        if (age.compareTo(tolerance) > 0)
            throw new SecurityException("webhook signature timestamp is outside the accepted window");

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            mac.update(Long.toString(parsed.timestamp()).getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            expected = mac.doFinal(rawBody);
        } catch (Exception failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        }
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(parsed.signature());
        } catch (IllegalArgumentException malformed) {
            throw new SecurityException("webhook signature is malformed", malformed);
        }
        if (supplied.length != expected.length || !MessageDigest.isEqual(expected, supplied))
            throw new SecurityException("webhook signature does not match");
    }

    private static ParsedHeader parse(String header) {
        if (header == null || header.length() > 160)
            throw new SecurityException("webhook signature header is missing or invalid");
        String[] parts = header.split(",", -1);
        if (parts.length != 2 || !parts[0].startsWith("t=") || !parts[1].startsWith("v1="))
            throw new SecurityException("webhook signature header must contain one timestamp and one v1 signature");
        try {
            long timestamp = Long.parseLong(parts[0].substring(2));
            String signature = parts[1].substring(3);
            if (timestamp < 0 || signature.length() != 64
                    || !signature.matches("[0-9a-f]{64}"))
                throw new SecurityException("webhook signature header is malformed");
            return new ParsedHeader(timestamp, signature);
        } catch (NumberFormatException invalid) {
            throw new SecurityException("webhook signature timestamp is malformed", invalid);
        }
    }

    private record ParsedHeader(long timestamp, String signature) { }
}
