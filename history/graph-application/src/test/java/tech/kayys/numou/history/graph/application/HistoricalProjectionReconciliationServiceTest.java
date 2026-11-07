package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionReconciliationStore;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionReconciliationServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    @Test void reportsMissingStaleDivergentAndOrphanedAndRepairsReplayableRows() throws Exception {
        UUID missing = UUID.randomUUID(), stale = UUID.randomUUID(), divergent = UUID.randomUUID(), orphan = UUID.randomUUID();
        var events = List.of(event(missing, 1), event(stale, 3), event(divergent, 2));
        FakeCanonical canonical = new FakeCanonical(events, Set.of(missing, stale, divergent));
        FakeProjection projection = new FakeProjection();
        projection.states.put(stale, new HistoricalProjectionReconciliationPort.ProjectionState(1, "old", "old-hash"));
        projection.states.put(divergent, new HistoricalProjectionReconciliationPort.ProjectionState(2, "wrong-key", "wrong-hash"));
        projection.ids.addAll(List.of(missing, stale, divergent, orphan));
        List<ClaimedProjectionEvent> repaired = new ArrayList<>();
        var service = new HistoricalProjectionReconciliationService(canonical, projection, repaired::add);
        var report = service.reconcile(TENANT, 2, true);
        assertEquals(3, report.canonicalChecked());
        assertEquals(1, report.missing());
        assertEquals(1, report.stale());
        assertEquals(1, report.divergent());
        assertEquals(1, report.orphaned());
        assertEquals(3, report.repaired());
        assertEquals(Set.of(missing, stale, divergent), new HashSet<>(repaired.stream().map(ClaimedProjectionEvent::aggregateId).toList()));
    }

    @Test void newerGraphRevisionIsReportedButNeverOverwritten() throws Exception {
        UUID id = UUID.randomUUID();
        var event = event(id, 1);
        FakeCanonical canonical = new FakeCanonical(List.of(event), Set.of(id));
        FakeProjection projection = new FakeProjection();
        projection.states.put(id, new HistoricalProjectionReconciliationPort.ProjectionState(2, "newer", "hash"));
        List<ClaimedProjectionEvent> repaired = new ArrayList<>();
        var report = new HistoricalProjectionReconciliationService(canonical, projection, repaired::add)
                .reconcile(TENANT, 10, true);
        assertEquals(1, report.divergent());
        assertTrue(repaired.isEmpty());
    }

    private static ClaimedProjectionEvent event(UUID id, long revision) {
        String payload = "{\"relationshipId\":\"" + id + "\",\"revision\":" + revision + "}";
        return new ClaimedProjectionEvent(UUID.randomUUID(), "event-" + id + "-" + revision,
                "history.relationship.revision-appended", 1, TENANT, id, revision, payload,
                NOW, 0, 0, UUID.randomUUID(), NOW);
    }

    private static final class FakeCanonical implements HistoricalProjectionReconciliationStore {
        private final List<ClaimedProjectionEvent> events;
        private final Set<UUID> ids;
        FakeCanonical(List<ClaimedProjectionEvent> events, Set<UUID> ids) {
            this.events=events.stream().sorted(Comparator.comparing(ClaimedProjectionEvent::aggregateId)).toList();
            this.ids=ids;
        }
        public List<ClaimedProjectionEvent> latestPage(UUID tenant, UUID after, int limit) {
            return events.stream().filter(e -> after == null || e.aggregateId().compareTo(after)>0).limit(limit).toList();
        }
        public boolean exists(UUID tenant, UUID id) { return ids.contains(id); }
    }

    private static final class FakeProjection implements HistoricalProjectionReconciliationPort {
        private final Map<UUID, ProjectionState> states = new HashMap<>();
        private final List<UUID> ids = new ArrayList<>();
        public Optional<ProjectionState> find(UUID tenant, UUID id) { return Optional.ofNullable(states.get(id)); }
        public List<UUID> listRelationshipIds(UUID tenant, UUID after, int limit) {
            return ids.stream().filter(id -> after == null || id.compareTo(after)>0).sorted().limit(limit).toList();
        }
    }
}
