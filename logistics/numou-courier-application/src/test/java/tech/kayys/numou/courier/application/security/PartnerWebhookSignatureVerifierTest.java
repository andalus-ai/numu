package tech.kayys.numou.courier.application.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PartnerWebhookSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final byte[] SECRET = "a-production-like-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY = "{\"eventId\":\"evt-1\",\"type\":\"shipment.updated\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsExactBodyAndFreshTimestamp() {
        assertDoesNotThrow(() -> PartnerWebhookSignatureVerifier.verify(
                BODY, signedHeader(BODY, NOW), SECRET, NOW, Duration.ofMinutes(5)));
    }

    @Test
    void rejectsBodyMutation() {
        byte[] changed = "{\"eventId\":\"evt-2\",\"type\":\"shipment.updated\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                changed, signedHeader(BODY, NOW), SECRET, NOW, Duration.ofMinutes(5)));
    }

    @Test
    void rejectsExpiredAndFutureSignaturesOutsideWindow() {
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                BODY, signedHeader(BODY, NOW.minusSeconds(301)), SECRET, NOW, Duration.ofMinutes(5)));
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                BODY, signedHeader(BODY, NOW.plusSeconds(301)), SECRET, NOW, Duration.ofMinutes(5)));
    }

    @Test
    void rejectsMalformedHeaderWeakKeyOversizedBodyAndExcessiveTolerance() {
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                BODY, "v1=bad,t=1", SECRET, NOW, Duration.ofMinutes(5)));
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                BODY, signedHeader(BODY, NOW), new byte[8], NOW, Duration.ofMinutes(5)));
        assertThrows(SecurityException.class, () -> PartnerWebhookSignatureVerifier.verify(
                new byte[1_048_577], signedHeader(BODY, NOW), SECRET, NOW, Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> PartnerWebhookSignatureVerifier.verify(
                BODY, signedHeader(BODY, NOW), SECRET, NOW, Duration.ofMinutes(11)));
    }

    private static String signedHeader(byte[] body, Instant at) {
        try {
            long timestamp = at.getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
