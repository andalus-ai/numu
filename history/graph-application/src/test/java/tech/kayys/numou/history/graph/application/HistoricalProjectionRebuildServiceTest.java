package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.ReplayEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionRebuildServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Test void checkpointAdvancesOnlyAfterWholePageProjects() {
        FakeStore store = new FakeStore();
        store.events = List.of(event(1), event(2));
        List<String> projected = new ArrayList<>();
        var service = new HistoricalProjectionRebuildService(store, e -> projected.add(e.eventKey()),
                allowAll(), Clock.fixed(NOW, ZoneOffset.UTC), 2);
        var run = service.start(TENANT);
        var progress = service.resumeOnePage(TENANT, run.runId());
        assertEquals(2, progress.projectedEvents());
        assertEquals(2, progress.run().cursorSequence());
        assertEquals(List.of("event-1", "event-2"), projected);
    }

    @Test void failedProjectionLeavesCursorForSafeReplay() {
        FakeStore store = new FakeStore();
        store.events = List.of(event(1), event(2));
        int[] calls = {0};
        var service = new HistoricalProjectionRebuildService(store, e -> {
            calls[0]++;
            if (calls[0] == 2) throw new IllegalStateException("temporary provider failure");
        }, allowAll(), Clock.fixed(NOW, ZoneOffset.UTC), 2);
        var run = service.start(TENANT);
        assertThrows(IllegalStateException.class, () -> service.resumeOnePage(TENANT, run.runId()));
        assertEquals(0, store.cursor);
    }

    @Test void completedRunIsIdempotent() {
        FakeStore store = new FakeStore();
        store.events = List.of(event(1));
        var service = new HistoricalProjectionRebuildService(store, e -> {}, allowAll(),
                Clock.fixed(NOW, ZoneOffset.UTC), 1);
        var run = service.start(TENANT);
        assertFalse(service.resumeOnePage(TENANT, run.runId()).completed());
        assertTrue(service.resumeOnePage(TENANT, run.runId()).completed());
        assertTrue(service.resumeOnePage(TENANT, run.runId()).completed());
    }

    @Test void tenantRebuildAuthorizationPrecedesStoreAccess() {
        FakeStore store = new FakeStore();
        var service = new HistoricalProjectionRebuildService(store, e -> {}, denyRebuild(),
                Clock.fixed(NOW, ZoneOffset.UTC), 1);
        assertThrows(SecurityException.class, () -> service.start(TENANT));
        assertEquals(0, store.startCalls);
    }

    @Test void publicRebuildUsesSeparatePermission() {
        FakeStore store = new FakeStore();
        var service = new HistoricalProjectionRebuildService(store, e -> {}, denyRebuild(),
                Clock.fixed(NOW, ZoneOffset.UTC), 1);
        assertThrows(SecurityException.class, () -> service.start(null));
        assertEquals(0, store.startCalls);
    }

    private static HistoricalProjectionMaintenanceAuthorizationPort allowAll() {
        return new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        };
    }
    private static HistoricalProjectionMaintenanceAuthorizationPort denyRebuild() {
        return new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) { throw new SecurityException("denied"); }
            public void requireCanRebuildPublicCorpus() { throw new SecurityException("denied"); }
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        };
    }
    private static ReplayEvent event(long sequence) {
        return new ReplayEvent(sequence, UUID.randomUUID(), "event-" + sequence,
                "history.relationship.revision-appended", 1, TENANT, UUID.randomUUID(),
                sequence, "{}", NOW);
    }
    private static final class FakeStore implements HistoricalProjectionRebuildStore {
        private List<ReplayEvent> events = List.of();
        private long cursor;
        private RebuildRun run;
        int startCalls;
        public RebuildRun start(UUID tenant, Instant now) {
            startCalls++;
            run = new RebuildRun(UUID.randomUUID(), tenant, events.size(), 0,
                    RebuildRun.Status.RUNNING, now, now);
            return run;
        }
        public Optional<RebuildRun> find(UUID tenant, UUID id) {
            return run != null && run.runId().equals(id) && Objects.equals(run.tenantId(), tenant)
                    ? Optional.of(run) : Optional.empty();
        }
        public List<ReplayEvent> nextPage(UUID tenant, UUID id, int limit) {
            return events.stream().filter(e -> e.sequence() > cursor).limit(limit).toList();
        }
        public boolean advance(UUID tenant, UUID id, long expected, long next, Instant now) {
            if (cursor != expected) return false;
            cursor = next;
            run = new RebuildRun(run.runId(), tenant, run.highWaterSequence(), cursor,
                    run.status(), run.createdAt(), now);
            return true;
        }
        public boolean complete(UUID tenant, UUID id, long expected, Instant now) {
            if (cursor != expected || events.stream().anyMatch(e -> e.sequence() > cursor)) return false;
            run = new RebuildRun(run.runId(), tenant, run.highWaterSequence(), cursor,
                    RebuildRun.Status.COMPLETED, run.createdAt(), now);
            return true;
        }
    }
}
