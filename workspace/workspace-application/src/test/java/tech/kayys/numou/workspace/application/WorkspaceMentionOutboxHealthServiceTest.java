package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxAdminPolicy;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxHealthStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMentionOutboxHealthServiceTest {
    @Test
    void authorizesBeforeReadingAndReturnsTenantSnapshot() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        AtomicBoolean authorized = new AtomicBoolean();
        WorkspaceMentionOutboxHealthStore store = (scope, capturedAt) -> {
            assertTrue(authorized.get(), "store must not be read before authorization");
            assertEquals(tenant, scope);
            assertEquals(now, capturedAt);
            return new WorkspaceMentionOutboxHealthStore.Snapshot(5, 2, 1, 1, 3,
                    now.minusSeconds(90));
        };
        WorkspaceMentionOutboxAdminPolicy policy = (scope, principal) -> {
            assertEquals(tenant, scope);
            assertEquals(actor, principal);
            authorized.set(true);
        };
        var service = new WorkspaceMentionOutboxHealthService(store, policy,
                Clock.fixed(now, ZoneOffset.UTC));

        var result = service.snapshot(tenant, actor);

        assertEquals(5, result.pending());
        assertEquals(2, result.due());
        assertEquals(1, result.leased());
        assertEquals(1, result.expiredLeases());
        assertEquals(3, result.dead());
        assertEquals(90, result.oldestPendingAge().toSeconds());
        assertEquals(now, result.capturedAt());
    }

    @Test
    void rejectsMissingTenantOrActorBeforePolicyOrStore() {
        var service = new WorkspaceMentionOutboxHealthService((tenant, now) -> {
            fail("store must not be read");
            return null;
        }, (tenant, actor) -> fail("policy must not run"), Clock.systemUTC());
        assertThrows(NullPointerException.class, () -> service.snapshot(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> service.snapshot(UUID.randomUUID(), null));
    }
}
