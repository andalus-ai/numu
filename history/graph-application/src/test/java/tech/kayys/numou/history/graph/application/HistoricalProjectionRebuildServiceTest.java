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
        HistoricalRelationshipProjectionPort port = e -> projected.add(e.eventKey());
        var service = service(store, port);
        var run = service.startTenantRebuild();
        var progress = service.resumeTenantPage(run.runId());
        assertEquals(2, progress.projectedEvents());
        assertEquals(2, progress.run().cursorSequence());
        assertEquals(List.of("event-1", "event-2"), projected);
    }

    @Test void failedProjectionLeavesCursorForSafeReplay() {
        FakeStore store = new FakeStore();
        store.events = List.of(event(1), event(2));
        int[] calls = {0};
        HistoricalRelationshipProjectionPort port = e -> {
            calls[0]++;
            if (calls[0] == 2) throw new IllegalStateException("temporary provider failure");
        };
        var service = service(store, port);
        var run = service.startTenantRebuild();
        assertThrows(IllegalStateException.class, () -> service.resumeTenantPage(run.runId()));
        assertEquals(0, store.cursor);
    }

    @Test void completedRunIsIdempotent() {
        FakeStore store = new FakeStore();
        store.events = List.of(event(1));
        var service = service(store, e -> {});
        var run = service.startTenantRebuild();
        assertFalse(service.resumeTenantPage(run.runId()).completed());
        assertTrue(service.resumeTenantPage(run.runId()).completed());
        assertTrue(service.resumeTenantPage(run.runId()).completed());
    }

    private static HistoricalProjectionRebuildService service(FakeStore store, HistoricalRelationshipProjectionPort port) {
        var auth = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) { assertEquals(TENANT, id); }
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        };
        return new HistoricalProjectionRebuildService(store, port, () -> TENANT, auth,
                Clock.fixed(NOW, ZoneOffset.UTC), 2);
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
        public RebuildRun start(UUID tenant, Instant now) {
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
