package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Run;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Status;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumable coordinator for staging, replaying, validating and activating one graph generation.
 * PostgreSQL persists the operation and rebuild cursor; Neo4j owns the active-generation pointer.
 * The two systems do not share an ACID transaction, so every transition is independently recoverable.
 */
public final class HistoricalProjectionGenerationOrchestrator {
    private final HistoricalProjectionGenerationRunStore runs;
    private final HistoricalProjectionGenerationService generations;
    private final HistoricalProjectionRebuildService rebuilds;
    private final HistoricalProjectionMetricsPort metrics;
    private final Clock clock;

    public HistoricalProjectionGenerationOrchestrator(HistoricalProjectionGenerationRunStore runs,
            HistoricalProjectionGenerationService generations, HistoricalProjectionRebuildService rebuilds,
            HistoricalProjectionMetricsPort metrics, Clock clock) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.generations = Objects.requireNonNull(generations, "generations");
        this.rebuilds = Objects.requireNonNull(rebuilds, "rebuilds");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The caller must reuse operationId for every retry of the same requested rebuild. */
    public Run start(UUID tenantId, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        // This authorized lookup is deliberately before journal access.
        var active = generations.active(tenantId);
        if (runs.find(tenantId, operationId).isEmpty()) {
            runs.create(tenantId, operationId, UUID.randomUUID(), UUID.randomUUID(),
                    active.map(g -> g.generationId()).orElse(null), clock.instant());
        }
        return resumeOneStep(tenantId, operationId);
    }

    /** Advances at most one bounded lifecycle step; safe to invoke repeatedly after interruption. */
    public Run resumeOneStep(UUID tenantId, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        generations.active(tenantId); // fail-closed authorization before journal lookup
        Run run = runs.find(tenantId, operationId)
                .orElseThrow(() -> new IllegalArgumentException("generation operation does not exist in this scope"));
        try {
            switch (run.status()) {
                case PREPARING -> {
                    generations.create(tenantId, run.generationId());
                    rebuilds.start(tenantId, run.rebuildRunId());
                    transition(run, Status.REPLAYING);
                }
                case REPLAYING -> {
                    var progress = rebuilds.resumeOnePage(tenantId, run.rebuildRunId(), run.generationId());
                    if (progress.completed()) transition(run, Status.VALIDATING);
                }
                case VALIDATING -> {
                    generations.markValidatedReady(tenantId, run.generationId());
                    transition(run, Status.READY);
                }
                case READY -> transition(run, Status.ACTIVATING);
                case ACTIVATING -> activateRecoverably(run);
                case ACTIVE -> { /* terminal, idempotent result */ }
            }
        } catch (RuntimeException failure) {
            try {
                runs.recordFailure(tenantId, operationId, safeMessage(failure), clock.instant());
            } catch (RuntimeException journalFailure) {
                failure.addSuppressed(journalFailure);
            }
            throw failure;
        }
        Run latest = runs.find(tenantId, operationId).orElseThrow();
        try {
            metrics.recordGenerationRun(new HistoricalProjectionMetricsPort.GenerationRunMetric(
                    latest.status().name(), latest.failureCount()));
        } catch (RuntimeException ignored) {
            // Observability must never invalidate committed lifecycle state.
        }
        return latest;
    }

    private void activateRecoverably(Run run) {
        var candidate = generations.find(run.tenantId(), run.generationId())
                .orElseThrow(() -> new IllegalStateException("staged generation disappeared"));
        if (candidate.status() == HistoricalProjectionGenerationStore.Status.ACTIVE
                || candidate.status() == HistoricalProjectionGenerationStore.Status.RETAINED) {
            // Neo4j may have committed activation before the PostgreSQL phase update was interrupted.
            transition(run, Status.ACTIVE);
            return;
        }
        generations.activate(run.tenantId(), run.generationId(), run.expectedActiveGenerationId());
        transition(run, Status.ACTIVE);
    }

    private void transition(Run run, Status next) {
        if (runs.transition(run.tenantId(), run.operationId(), run.status(), next, clock.instant())) return;
        Run latest = runs.find(run.tenantId(), run.operationId()).orElseThrow();
        if (latest.status() != next) {
            throw new IllegalStateException("generation operation transition conflict: expected "
                    + run.status() + " but found " + latest.status());
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
