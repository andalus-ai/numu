package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.in.PosDeadLetterReplayAuthorizer;
import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPosDeadLetterTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void validatesOperatorAndReasonBeforeCallingAuthorizationOrPersistence() {
        AtomicBoolean authorized = new AtomicBoolean(false);
        AtomicBoolean persisted = new AtomicBoolean(false);
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(
                operations((t, e, a, r, at) -> { persisted.set(true); return true; }),
                (tenant, event, actor) -> { authorized.set(true); return true; },
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), " ", "manual retry"));
        assertFalse(authorized.get());
        assertFalse(persisted.get());
    }

    @Test
    void deniesUnauthorizedTenantScopedReplayBeforePersistence() {
        AtomicBoolean persisted = new AtomicBoolean(false);
        UUID tenant = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(
                operations((t, e, a, r, at) -> { persisted.set(true); return true; }),
                (requestedTenant, requestedEvent, actor) -> false,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(SecurityException.class,
                () -> useCase.execute(tenant, event, "operator-1", "manual retry"));
        assertFalse(persisted.get());
    }

    @Test
    void authorizesExactTenantAndEventThenForwardsAuditContext() {
        UUID tenant = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        AtomicReference<String> captured = new AtomicReference<>();
        PosDeadLetterReplayAuthorizer authorizer = (requestedTenant, requestedEvent, actor) ->
                tenant.equals(requestedTenant) && event.equals(requestedEvent) && actor.equals("operator-1");
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(
                operations((t, e, a, r, at) -> {
                    captured.set(t + "|" + e + "|" + a + "|" + r + "|" + at);
                    return true;
                }),
                authorizer,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(useCase.execute(tenant, event, " operator-1 ", " network restored "));
        assertEquals(tenant + "|" + event + "|operator-1|network restored|" + NOW, captured.get());
    }

    @Test
    void doesNotPersistWhenAuthorizationRejectsDifferentTenant() {
        UUID tenant = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        AtomicBoolean persisted = new AtomicBoolean(false);
        ReplayPosDeadLetter useCase = new ReplayPosDeadLetter(
                operations((t, e, a, r, at) -> { persisted.set(true); return true; }),
                (requestedTenant, requestedEvent, actor) -> tenant.equals(requestedTenant)
                        && event.equals(requestedEvent),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(SecurityException.class,
                () -> useCase.execute(UUID.randomUUID(), event, "operator-1", "manual retry"));
        assertFalse(persisted.get());
    }

    private static PosOutboxOperations operations(Replay replay) {
        return new PosOutboxOperations() {
            @Override
            public Map<String, Long> countByDeliveryStatus() {
                return Map.of();
            }

            @Override
            public boolean replayDeadLetter(
                    UUID tenantId, UUID eventId, String actor, String reason, Instant replayedAt) {
                return replay.run(tenantId, eventId, actor, reason, replayedAt);
            }
        };
    }

    @FunctionalInterface
    private interface Replay {
        boolean run(UUID tenantId, UUID eventId, String actor, String reason, Instant replayedAt);
    }
}
