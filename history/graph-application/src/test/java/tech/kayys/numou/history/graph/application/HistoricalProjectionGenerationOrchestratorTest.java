package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionGenerationOrchestratorTest {
    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");

    @Test void resumesDurableLifecycleAndActivatesOnlyAfterReplayAndValidation() {
        Fixture f = new Fixture();
        UUID operation = UUID.randomUUID();

        var first = f.orchestrator.start(null, operation);
        assertEquals(HistoricalProjectionGenerationRunStore.Status.REPLAYING, first.status());
        UUID generationId = first.generationId();

        assertEquals(HistoricalProjectionGenerationRunStore.Status.VALIDATING,
                f.orchestrator.resumeOneStep(null, operation).status());
        assertEquals(HistoricalProjectionGenerationRunStore.Status.READY,
                f.orchestrator.resumeOneStep(null, operation).status());
        assertEquals(HistoricalProjectionGenerationRunStore.Status.ACTIVATING,
                f.orchestrator.resumeOneStep(null, operation).status());
        assertEquals(HistoricalProjectionGenerationRunStore.Status.ACTIVE,
                f.orchestrator.resumeOneStep(null, operation).status());

        assertEquals(generationId, f.generations.active(null).orElseThrow().generationId());
        assertEquals(HistoricalProjectionGenerationRunStore.Status.ACTIVE,
                f.orchestrator.resumeOneStep(null, operation).status());
    }

    @Test void retriesActivationWhenNeo4jCommittedBeforeJournalTransition() {
        Fixture f = new Fixture();
        UUID operation = UUID.randomUUID();
        f.orchestrator.start(null, operation);
        f.orchestrator.resumeOneStep(null, operation); // replay complete
        f.orchestrator.resumeOneStep(null, operation); // validated
        f.orchestrator.resumeOneStep(null, operation); // READY -> ACTIVATING
        f.runs.failNextActiveTransition = true;

        assertThrows(IllegalStateException.class, () -> f.orchestrator.resumeOneStep(null, operation));
        var afterFailure = f.runs.find(null, operation).orElseThrow();
        assertEquals(HistoricalProjectionGenerationRunStore.Status.ACTIVATING, afterFailure.status());
        assertEquals(1, afterFailure.failureCount());
        assertEquals(HistoricalProjectionGenerationStore.Status.ACTIVE,
                f.generations.find(null, afterFailure.generationId()).orElseThrow().status());

        assertEquals(HistoricalProjectionGenerationRunStore.Status.ACTIVE,
                f.orchestrator.resumeOneStep(null, operation).status());
    }

    private static final class Fixture {
        final FakeGenerationStore generations = new FakeGenerationStore();
        final FakeRunStore runs = new FakeRunStore();
        final FakeRebuildStore rebuildStore = new FakeRebuildStore();
        final HistoricalProjectionMaintenanceAuthorizationPort authorization = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID tenant) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID tenant) {}
            public void requireCanAuditPublicCorpus() {}
        };
        final HistoricalProjectionGenerationService generationService =
                new HistoricalProjectionGenerationService(generations, authorization,
                        (tenant, generation) -> new HistoricalProjectionGenerationValidationPort.ValidationResult(
                                true, 0, 0, 0, 0, 0, 0, "ok"), clock());
        final HistoricalProjectionRebuildService rebuildService =
                new HistoricalProjectionRebuildService(rebuildStore, new TargetedProjector(),
                        () -> UUID.randomUUID(), authorization, clock(), 10);
        final HistoricalProjectionGenerationOrchestrator orchestrator =
                new HistoricalProjectionGenerationOrchestrator(runs, generationService,
                        rebuildService, snapshot -> {}, clock());
    }

    private static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static final class FakeRunStore implements HistoricalProjectionGenerationRunStore {
        final Map<UUID, Run> data = new HashMap<>();
        boolean failNextActiveTransition;
        public Run create(UUID tenant, UUID operation, UUID generation, UUID rebuild,
                          UUID expected, Instant now) {
            return data.computeIfAbsent(operation, ignored -> new Run(operation, tenant, generation,
                    rebuild, expected, Status.PREPARING, 0, "", now, now, null));
        }
        public Optional<Run> find(UUID tenant, UUID operation) {
            return Optional.ofNullable(data.get(operation)).filter(r -> Objects.equals(r.tenantId(), tenant));
        }
        public boolean transition(UUID tenant, UUID operation, Status expected, Status next, Instant now) {
            Run r = find(tenant, operation).orElseThrow();
            if (r.status() != expected) return false;
            if (next == Status.ACTIVE && failNextActiveTransition) {
                failNextActiveTransition = false;
                return false;
            }
            data.put(operation, new Run(r.operationId(), r.tenantId(), r.generationId(), r.rebuildRunId(),
                    r.expectedActiveGenerationId(), next, r.failureCount(), r.lastError(),
                    r.createdAt(), now, next == Status.ACTIVE ? now : r.activatedAt()));
            return true;
        }
        public void recordFailure(UUID tenant, UUID operation, String detail, Instant now) {
            Run r = find(tenant, operation).orElseThrow();
            data.put(operation, new Run(r.operationId(), r.tenantId(), r.generationId(), r.rebuildRunId(),
                    r.expectedActiveGenerationId(), r.status(), r.failureCount()+1, detail,
                    r.createdAt(), now, r.activatedAt()));
        }
    }

    private static final class FakeGenerationStore implements HistoricalProjectionGenerationStore {
        final Map<String, Generation> data = new HashMap<>();
        final Map<String, UUID> active = new HashMap<>();
        private String key(UUID tenant, UUID generation) { return scope(tenant)+":"+generation; }
        private String scope(UUID tenant) { return tenant == null ? "public" : tenant.toString(); }
        public Generation create(UUID tenant, UUID id, Instant now) {
            Generation g = new Generation(id, tenant, Status.BUILDING, now, now);
            if (data.putIfAbsent(key(tenant,id), g) != null) throw new IllegalStateException("exists");
            return g;
        }
        public Optional<Generation> find(UUID tenant, UUID id) { return Optional.ofNullable(data.get(key(tenant,id))); }
        public Optional<Generation> active(UUID tenant) {
            UUID id=active.get(scope(tenant)); return id==null?Optional.empty():find(tenant,id);
        }
        public boolean markReady(UUID tenant, UUID id, Instant now) {
            String k=key(tenant,id); Generation g=data.get(k);
            if(g==null||g.status()!=Status.BUILDING)return false;
            data.put(k,new Generation(id,tenant,Status.READY,g.createdAt(),now)); return true;
        }
        public boolean activate(UUID tenant, UUID id, UUID expected, Instant now) {
            String scope=scope(tenant);
            if(!Objects.equals(active.get(scope),expected))return false;
            Generation next=data.get(key(tenant,id));
            if(next==null||(next.status()!=Status.READY&&next.status()!=Status.RETAINED))return false;
            UUID previous=active.get(scope);
            if(previous!=null) {
                String oldKey=key(tenant,previous); Generation old=data.get(oldKey);
                data.put(oldKey,new Generation(previous,tenant,Status.RETAINED,old.createdAt(),now));
            }
            data.put(key(tenant,id),new Generation(id,tenant,Status.ACTIVE,next.createdAt(),now));
            active.put(scope,id); return true;
        }
    }

    private static final class FakeRebuildStore implements HistoricalProjectionRebuildStore {
        final Map<UUID, RebuildRun> runs = new HashMap<>();
        public RebuildRun start(UUID tenant, Instant now) { return start(tenant, UUID.randomUUID(), now); }
        public RebuildRun start(UUID tenant, UUID id, Instant now) {
            return runs.computeIfAbsent(id, ignored -> new RebuildRun(id,tenant,0,0,RebuildRun.Status.RUNNING,now,now));
        }
        public Optional<RebuildRun> find(UUID tenant, UUID id) {
            return Optional.ofNullable(runs.get(id)).filter(r -> Objects.equals(r.tenantId(),tenant));
        }
        public List<ReplayEvent> nextPage(UUID tenant, UUID id, int limit) { return List.of(); }
        public boolean advance(UUID tenant, UUID id, long expected, long next, Instant now) { return false; }
        public boolean complete(UUID tenant, UUID id, long expected, Instant now) {
            RebuildRun r=find(tenant,id).orElseThrow();
            if(r.status()!=RebuildRun.Status.RUNNING||r.cursorSequence()!=expected)return false;
            runs.put(id,new RebuildRun(id,tenant,r.highWaterSequence(),r.cursorSequence(),
                    RebuildRun.Status.COMPLETED,r.createdAt(),now)); return true;
        }
    }

    private static final class TargetedProjector implements GenerationTargetedHistoricalRelationshipProjectionPort {
        public void project(ClaimedProjectionEvent event) {}
        public void projectIntoGeneration(ClaimedProjectionEvent event, UUID generationId) {}
    }
}
