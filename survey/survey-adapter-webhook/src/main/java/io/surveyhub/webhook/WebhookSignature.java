package io.surveyhub.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Header format: {@code X-Survey-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of "<t>.<raw body>">}.
 * Receivers must verify against the RAW request body, compare in constant time and reject old timestamps (replay protection).
 */
public final class WebhookSignature {
    public static final String HEADER = "X-Survey-Signature";

    private WebhookSignature() {}

    public static String sign(String secret, long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + hmac(secret, timestamp + "." + body);
    }

    /** Receiver-side check; returns false for anything malformed, forged, or outside the tolerance window. */
    public static boolean verify(String secret, String header, String body, Duration tolerance, Clock clock) {
        if (header == null || header.length() > 300) return false;
        Long t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String p = part.trim();
            if (p.startsWith("t=")) { try { t = Long.parseLong(p.substring(2)); } catch (NumberFormatException e) { return false; } }
            else if (p.startsWith("v1=")) v1 = p.substring(3);
        }
        if (t == null || v1 == null) return false;
        if (Math.abs(clock.instant().getEpochSecond() - t) > tolerance.getSeconds()) return false;
        return MessageDigest.isEqual(hmac(secret, t + "." + body).getBytes(StandardCharsets.US_ASCII), v1.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
