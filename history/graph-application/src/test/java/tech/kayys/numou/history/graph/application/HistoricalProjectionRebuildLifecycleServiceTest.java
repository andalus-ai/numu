package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.Action;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionRebuildLifecycleServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACTOR = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    @Test void pauseResumeAndCancelAreVersionFencedAndActorAttributed() {
        FakeStore store = new FakeStore();
        var service = service(store, allowAll());
        RebuildRun paused = service.pause(TENANT, store.run.runId(), "maintenance window");
        assertEquals(RebuildRun.Status.PAUSED, paused.status());
        assertEquals(1, paused.lifecycleVersion());
        assertEquals(ACTOR, store.lastActor);
        assertEquals(Action.PAUSE, store.lastAction);

        RebuildRun resumed = service.resume(TENANT, store.run.runId(), "maintenance complete");
        assertEquals(RebuildRun.Status.RUNNING, resumed.status());
        assertEquals(2, resumed.lifecycleVersion());

        RebuildRun cancelled = service.cancel(TENANT, store.run.runId(), "operator requested");
        assertEquals(RebuildRun.Status.CANCELLED, cancelled.status());
        assertEquals(3, cancelled.lifecycleVersion());
    }

    @Test void failedRunCanOnlyBeRetriedThroughExplicitAuditedTransition() {
        FakeStore store = new FakeStore();
        var service = service(store, allowAll());
        RebuildRun failed = service.markFailed(TENANT, store.run.runId(), "projection provider unavailable");
        assertEquals(RebuildRun.Status.FAILED, failed.status());
        assertEquals("projection provider unavailable", failed.failureDetail());
        RebuildRun retried = service.retry(TENANT, store.run.runId(), "provider recovered");
        assertEquals(RebuildRun.Status.RUNNING, retried.status());
        assertEquals("", retried.failureDetail());
        assertEquals(Action.RETRY, store.lastAction);
    }

    @Test void authorizationPrecedesRunLookupAndOperatorResolution() {
        FakeStore store = new FakeStore();
        var service = service(store, new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) { throw new SecurityException("denied"); }
            public void requireCanRebuildPublicCorpus() { throw new SecurityException("denied"); }
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        });
        assertThrows(SecurityException.class, () -> service.pause(TENANT, store.run.runId(), "x"));
        assertEquals(0, store.findCalls);
    }

    private static HistoricalProjectionRebuildLifecycleService service(FakeStore store,
            HistoricalProjectionMaintenanceAuthorizationPort auth) {
        return new HistoricalProjectionRebuildLifecycleService(store, () -> TENANT, auth,
                () -> ACTOR, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static HistoricalProjectionMaintenanceAuthorizationPort allowAll() {
        return new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        };
    }

    private static final class FakeStore implements HistoricalProjectionRebuildStore {
        RebuildRun run = new RebuildRun(UUID.randomUUID(), TENANT, 10, 0,
                RebuildRun.Status.RUNNING, NOW, NOW, 0, "");
        UUID lastActor;
        Action lastAction;
        int findCalls;

        public RebuildRun start(UUID tenantId, Instant now) { throw new UnsupportedOperationException(); }
        public Optional<RebuildRun> find(UUID tenantId, UUID runId) {
            findCalls++;
            return run.runId().equals(runId) && TENANT.equals(tenantId) ? Optional.of(run) : Optional.empty();
        }
        public java.util.List<HistoricalProjectionRebuildStore.ReplayEvent> nextPage(UUID tenantId, UUID runId, int limit) {
            return java.util.List.of();
        }
        public boolean advance(UUID tenantId, UUID runId, long expectedCursor, long nextCursor, Instant now) {
            throw new UnsupportedOperationException();
        }
        public boolean complete(UUID tenantId, UUID runId, long expectedCursor, Instant now) {
            throw new UnsupportedOperationException();
        }
        public boolean transition(UUID tenantId, UUID runId, UUID actorId, Action action,
                long expectedLifecycleVersion, RebuildRun.Status expectedStatus,
                RebuildRun.Status targetStatus, String detail, Instant now) {
            if (run.lifecycleVersion() != expectedLifecycleVersion || run.status() != expectedStatus) return false;
            lastActor = actorId;
            lastAction = action;
            run = new RebuildRun(run.runId(), run.tenantId(), run.highWaterSequence(), run.cursorSequence(),
                    targetStatus, run.createdAt(), now, expectedLifecycleVersion + 1,
                    targetStatus == RebuildRun.Status.FAILED ? detail : "");
            return true;
        }
    }
}
