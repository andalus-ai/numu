package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPosDeadLetterTest {
    @Test
    void validatesOperatorAndReasonBeforeCallingPort() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        PosOutboxOperations port = new PosOutboxOperations() {
            public Map<String, Long> countByDeliveryStatus() { return Map.of(); }
            public boolean replayDeadLetter(UUID t, UUID e, String a, String r, Instant at) {
                called.set(true); return true;
            }
        };
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(port,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), " ", "manual retry"));
        assertFalse(called.get());
    }

    @Test
    void forwardsTenantAndAuditContext() {
        UUID tenant = UUID.randomUUID(), event = UUID.randomUUID();
        AtomicReference<String> captured = new AtomicReference<>();
        PosOutboxOperations port = new PosOutboxOperations() {
            public Map<String, Long> countByDeliveryStatus() { return Map.of(); }
            public boolean replayDeadLetter(UUID t, UUID e, String a, String r, Instant at) {
                captured.set(t + "|" + e + "|" + a + "|" + r + "|" + at); return true;
            }
        };
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(port, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(useCase.execute(tenant, event, " operator ", " network restored "));
        assertEquals(tenant + "|" + event + "|operator|network restored|" + now, captured.get());
    }
}
