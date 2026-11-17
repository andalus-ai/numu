package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Run;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Status;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.ReplayEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionGenerationOrchestratorTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    @Test void resumesThroughReplayValidationAndAtomicActivation() {
        var generationStore = new FakeGenerationStore();
        var runStore = new FakeRunStore();
        var auth = allowAll();
        var generationService = new HistoricalProjectionGenerationService(generationStore, auth,
                (tenant, generation) -> new HistoricalProjectionGenerationValidationPort.ValidationResult(
                        true, 0, 0, 0, 0, 0, 0, "clean"), clock());
        var rebuildService = rebuildService(new FakeRebuildStore(), auth);
        var coordinator = new HistoricalProjectionGenerationOrchestrator(runStore, generationService,
                rebuildService, snapshot -> {}, clock());

        UUID operation = UUID.randomUUID();
        Run run = coordinator.start(TENANT, operation);
        assertEquals(Status.REPLAYING, run.status());
        assertEquals(Status.VALIDATING, coordinator.resumeOneStep(TENANT, operation).status());
        assertEquals(Status.READY, coordinator.resumeOneStep(TENANT, operation).status());
        assertEquals(Status.ACTIVATING, coordinator.resumeOneStep(TENANT, operation).status());
        Run active = coordinator.resumeOneStep(TENANT, operation);

        assertEquals(Status.ACTIVE, active.status());
        assertEquals(active.generationId(), generationService.active(TENANT).orElseThrow().generationId());
        assertNotNull(active.activatedAt());
    }

    @Test void sameOperationIdResumesExistingGenerationInsteadOfCreatingAnother() {
        var generationStore = new FakeGenerationStore();
        var runStore = new FakeRunStore();
        var auth = allowAll();
        var generationService = new HistoricalProjectionGenerationService(generationStore, auth,
                (tenant, generation) -> new HistoricalProjectionGenerationValidationPort.ValidationResult(
                        true, 0, 0, 0, 0, 0, 0, "clean"), clock());
        var coordinator = new HistoricalProjectionGenerationOrchestrator(runStore, generationService,
                rebuildService(new FakeRebuildStore(), auth), snapshot -> {}, clock());

        UUID operation = UUID.randomUUID();
        Run first = coordinator.start(TENANT, operation);
        Run retry = coordinator.start(TENANT, operation);

        assertEquals(first.generationId(), retry.generationId());
        assertEquals(first.rebuildRunId(), retry.rebuildRunId());
        assertEquals(1, generationStore.generations.size());
        assertEquals(1, runStore.runs.size());
    }

    @Test void failedValidationDoesNotActivateCandidateAndCanBeRetried() {
        var generationStore = new FakeGenerationStore();
        var runStore = new FakeRunStore();
        var auth = allowAll();
        var generationService = new HistoricalProjectionGenerationService(generationStore, auth,
                (tenant, generation) -> new HistoricalProjectionGenerationValidationPort.ValidationResult(
                        false, 1, 0, 1, 0, 0, 0, "projection incomplete"), clock());
        var coordinator = new HistoricalProjectionGenerationOrchestrator(runStore, generationService,
                rebuildService(new FakeRebuildStore(), auth), snapshot -> {}, clock());

        UUID operation = UUID.randomUUID();
        coordinator.start(TENANT, operation);
        coordinator.resumeOneStep(TENANT, operation); // replay completes
        assertThrows(IllegalStateException.class, () -> coordinator.resumeOneStep(TENANT, operation));

        Run failed = runStore.find(TENANT, operation).orElseThrow();
        assertEquals(Status.VALIDATING, failed.status());
        assertEquals(1, failed.failureCount());
        assertTrue(generationService.active(TENANT).isEmpty());
    }

    @Test void recoversWhenNeo4jActivationCommittedBeforePostgresJournalUpdate() {
        var generationStore = new FakeGenerationStore();
        var runStore = new FakeRunStore();
        var auth = allowAll();
        var generationService = new HistoricalProjectionGenerationService(generationStore, auth,
                (tenant, generation) -> new HistoricalProjectionGenerationValidationPort.ValidationResult(
                        true, 0, 0, 0, 0, 0, 0, "clean"), clock());
        var coordinator = new HistoricalProjectionGenerationOrchestrator(runStore, generationService,
                rebuildService(new FakeRebuildStore(), auth), snapshot -> {}, clock());

        UUID operation = UUID.randomUUID();
        Run run = coordinator.start(TENANT, operation);
        coordinator.resumeOneStep(TENANT, operation); // REPLAYING -> VALIDATING
        coordinator.resumeOneStep(TENANT, operation); // VALIDATING -> READY
        coordinator.resumeOneStep(TENANT, operation); // READY -> ACTIVATING

        generationService.create(TENANT, run.generationId());
        generationService.markValidatedReady(TENANT, run.generationId());
        generationService.activate(TENANT, run.generationId(), null); // Neo4j committed; journal still ACTIVATING

        Run recovered = coordinator.resumeOneStep(TENANT, operation);
        assertEquals(Status.ACTIVE, recovered.status());
        assertEquals(run.generationId(), generationService.active(TENANT).orElseThrow().generationId());
    }

    private static HistoricalProjectionRebuildService rebuildService(FakeRebuildStore store,
            HistoricalProjectionMaintenanceAuthorizationPort auth) {
        GenerationTargetedHistoricalRelationshipProjectionPort projector = new GenerationTargetedHistoricalRelationshipProjectionPort() {
            public void project(tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent event) {}
            public void projectIntoGeneration(tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent event,
                    UUID generationId) {}
        };
        return new HistoricalProjectionRebuildService(store, projector, () -> TENANT, auth, clock(), 20);
    }

    private static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static HistoricalProjectionMaintenanceAuthorizationPort allowAll() {
        return new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) {}
            public void requireCanAuditPublicCorpus() {}
        };
    }

    private static final class FakeRunStore implements HistoricalProjectionGenerationRunStore {
        final Map<String, Run> runs = new HashMap<>();
        private String key(UUID tenant, UUID operation) { return (tenant == null ? "public" : tenant.toString()) + "/" + operation; }
        public Run create(UUID tenant, UUID operation, UUID generation, UUID rebuild, UUID expected, Instant now) {
            return runs.computeIfAbsent(key(tenant, operation), ignored ->
                    new Run(operation, tenant, generation, rebuild, expected, Status.PREPARING, 0, "", now, now, null));
        }
        public Optional<Run> find(UUID tenant, UUID operation) { return Optional.ofNullable(runs.get(key(tenant, operation))); }
        public boolean transition(UUID tenant, UUID operation, Status expected, Status next, Instant now) {
            String k = key(tenant, operation); Run run = runs.get(k);
            if (run == null || run.status() != expected || !allowed(expected, next)) return false;
            runs.put(k, new Run(run.operationId(), tenant, run.generationId(), run.rebuildRunId(),
                    run.expectedActiveGenerationId(), next, run.failureCount(), run.lastError(),
                    run.createdAt(), now, next == Status.ACTIVE ? now : run.activatedAt()));
            return true;
        }
        public void recordFailure(UUID tenant, UUID operation, String detail, Instant now) {
            String k = key(tenant, operation); Run run = Objects.requireNonNull(runs.get(k));
            runs.put(k, new Run(run.operationId(), tenant, run.generationId(), run.rebuildRunId(),
                    run.expectedActiveGenerationId(), run.status(), run.failureCount() + 1,
                    detail, run.createdAt(), now, run.activatedAt()));
        }
        private boolean allowed(Status from, Status to) {
            return switch (from) {
                case PREPARING -> to == Status.REPLAYING;
                case REPLAYING -> to == Status.VALIDATING;
                case VALIDATING -> to == Status.READY;
                case READY -> to == Status.ACTIVATING;
                case ACTIVATING -> to == Status.ACTIVE;
                case ACTIVE -> false;
            };
        }
    }

    private static final class FakeRebuildStore implements HistoricalProjectionRebuildStore {
        final Map<UUID, RebuildRun> runs = new HashMap<>();
        public RebuildRun start(UUID tenant, Instant now) { return start(tenant, UUID.randomUUID(), now); }
        public RebuildRun start(UUID tenant, UUID id, Instant now) {
            return runs.computeIfAbsent(id, ignored -> new RebuildRun(id, tenant, 0, 0,
                    RebuildRun.Status.RUNNING, now, now));
        }
        public Optional<RebuildRun> find(UUID tenant, UUID id) {
            return Optional.ofNullable(runs.get(id)).filter(r -> Objects.equals(r.tenantId(), tenant));
        }
        public List<ReplayEvent> nextPage(UUID tenant, UUID id, int limit) { return List.of(); }
        public boolean advance(UUID tenant, UUID id, long expected, long next, Instant now) { return false; }
        public boolean complete(UUID tenant, UUID id, long expected, Instant now) {
            RebuildRun run = runs.get(id);
            if (run == null || run.cursorSequence() != expected) return false;
            runs.put(id, new RebuildRun(id, tenant, 0, 0, RebuildRun.Status.COMPLETED, run.createdAt(), now));
            return true;
        }
    }

    private static final class FakeGenerationStore implements HistoricalProjectionGenerationStore {
        final Map<String, HistoricalProjectionGenerationStore.Generation> generations = new HashMap<>();
        final Map<String, UUID> active = new HashMap<>();
        private String scope(UUID tenant) { return tenant == null ? "public" : tenant.toString(); }
        private String key(UUID tenant, UUID id) { return scope(tenant) + "/" + id; }
        public HistoricalProjectionGenerationStore.Generation create(UUID tenant, UUID id, Instant now) {
            HistoricalProjectionGenerationStore.Generation generation = new HistoricalProjectionGenerationStore.Generation(id, tenant, HistoricalProjectionGenerationStore.Status.BUILDING, now, now);
            generations.put(key(tenant, id), generation);
            return generation;
        }
        public Optional<HistoricalProjectionGenerationStore.Generation> find(UUID tenant, UUID id) { return Optional.ofNullable(generations.get(key(tenant, id))); }
        public Optional<HistoricalProjectionGenerationStore.Generation> active(UUID tenant) {
            UUID id = active.get(scope(tenant));
            return id == null ? Optional.empty() : find(tenant, id);
        }
        public boolean markReady(UUID tenant, UUID id, Instant now) {
            String k = key(tenant, id); HistoricalProjectionGenerationStore.Generation current = generations.get(k);
            if (current == null || current.status() != HistoricalProjectionGenerationStore.Status.BUILDING) return false;
            generations.put(k, new HistoricalProjectionGenerationStore.Generation(id, tenant, HistoricalProjectionGenerationStore.Status.READY, current.createdAt(), now));
            return true;
        }
        public boolean activate(UUID tenant, UUID id, UUID expected, Instant now) {
            UUID currentId = active.get(scope(tenant));
            if (!Objects.equals(currentId, expected)) return false;
            HistoricalProjectionGenerationStore.Generation candidate = generations.get(key(tenant, id));
            if (candidate == null || candidate.status() != HistoricalProjectionGenerationStore.Status.READY) return false;
            if (currentId != null) {
                HistoricalProjectionGenerationStore.Generation previous = generations.get(key(tenant, currentId));
                generations.put(key(tenant, currentId), new HistoricalProjectionGenerationStore.Generation(currentId, tenant, HistoricalProjectionGenerationStore.Status.RETAINED,
                        previous.createdAt(), now));
            }
            generations.put(key(tenant, id), new HistoricalProjectionGenerationStore.Generation(id, tenant, HistoricalProjectionGenerationStore.Status.ACTIVE, candidate.createdAt(), now));
            active.put(scope(tenant), id);
            return true;
        }
    }
}
