package tech.kayys.numou.feedback.application.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PublicInvitationRateLimiterTest {
    @Test
    void hashesTokenAndUsesUtcMinuteWindow() {
        String[] capturedHash = new String[1];
        Instant[] capturedWindow = new Instant[1];
        int[] capturedLimit = new int[1];
        var limiter = new PublicInvitationRateLimiter((hash, window, limit) -> {
            capturedHash[0] = hash;
            capturedWindow[0] = window;
            capturedLimit[0] = limit;
            return true;
        }, 42);

        Instant now = Instant.parse("2026-11-14T06:51:37Z");
        assertTrue(limiter.allow("opaque-bearer-token", now));
        assertNotEquals("opaque-bearer-token", capturedHash[0]);
        assertEquals(64, capturedHash[0].length());
        assertEquals(Instant.parse("2026-11-14T06:51:00Z"), capturedWindow[0]);
        assertEquals(42, capturedLimit[0]);
    }

    @Test
    void rejectsMalformedTokenWithoutTouchingRepository() {
        int[] calls = {0};
        var limiter = new PublicInvitationRateLimiter((hash, window, limit) -> {
            calls[0]++;
            return true;
        }, 10);
        assertFalse(limiter.allow(" ", Instant.now()));
        assertFalse(limiter.allow("x".repeat(129), Instant.now()));
        assertEquals(0, calls[0]);
    }
}