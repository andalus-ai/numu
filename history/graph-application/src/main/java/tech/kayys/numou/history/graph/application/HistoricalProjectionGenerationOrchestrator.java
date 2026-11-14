package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Run;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Status;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumable coordinator for staging, replaying, validating and activating one graph generation.
 * PostgreSQL persists the saga/checkpoint; Neo4j owns the generation pointer. Recovery is
 * deliberately stepwise because the two stores do not share an ACID transaction.
 */
public final class HistoricalProjectionGenerationOrchestrator {
    private final HistoricalProjectionGenerationRunStore runs;
    private final HistoricalProjectionGenerationService generations;
    private final HistoricalProjectionRebuildService rebuilds;
    private final HistoricalProjectionMetricsPort metrics;
    private final Clock clock;

    public HistoricalProjectionGenerationOrchestrator(
            HistoricalProjectionGenerationRunStore runs,
            HistoricalProjectionGenerationService generations,
            HistoricalProjectionRebuildService rebuilds,
            HistoricalProjectionMetricsPort metrics,
            Clock clock) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.generations = Objects.requireNonNull(generations, "generations");
        this.rebuilds = Objects.requireNonNull(rebuilds, "rebuilds");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** operationId is the caller's idempotency key and must be reused on retries. */
    public Run start(UUID tenantId, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        var existing = runs.find(tenantId, operationId);
        if (existing.isPresent()) return resumeOneStep(tenantId, operationId);
        UUID expectedActive = generations.active(tenantId).map(Generation::generationId).orElse(null);
        runs.create(tenantId, operationId, UUID.randomUUID(), UUID.randomUUID(),
                expectedActive, clock.instant());
        return resumeOneStep(tenantId, operationId);
    }

    /** Performs one bounded lifecycle step; call again with the same operation ID to continue. */
    public Run resumeOneStep(UUID tenantId, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
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
                case ACTIVE -> { /* idempotent terminal state */ }
            }
        } catch (RuntimeException failure) {
            runs.recordFailure(tenantId, operationId, safeMessage(failure), clock.instant());
            throw failure;
        }
        Run latest = runs.find(tenantId, operationId).orElseThrow();
        recordMetric(latest);
        return latest;
    }

    private void activateRecoverably(Run run) {
        Generation candidate = generations.find(run.tenantId(), run.generationId())
                .orElseThrow(() -> new IllegalStateException("staged generation disappeared"));
        if (candidate.status() == HistoricalProjectionGenerationStore.Status.ACTIVE
                || candidate.status() == HistoricalProjectionGenerationStore.Status.RETAINED) {
            // Neo4j may commit activation just before the PostgreSQL journal update is interrupted.
            transition(run, Status.ACTIVE);
            return;
        }
        generations.activate(run.tenantId(), run.generationId(), run.expectedActiveGenerationId());
        transition(run, Status.ACTIVE);
    }

    private void transition(Run run, Status next) {
        if (!runs.transition(run.tenantId(), run.operationId(), run.status(), next, clock.instant())) {
            Run latest = runs.find(run.tenantId(), run.operationId()).orElseThrow();
            if (latest.status() != next) {
                throw new IllegalStateException("generation operation transition conflict: expected "
                        + run.status() + " but found " + latest.status());
            }
        }
    }

    private void recordMetric(Run run) {
        try {
            metrics.recordGenerationRun(new HistoricalProjectionMetricsPort.GenerationRunMetric(
                    run.status().name(), run.failureCount()));
        } catch (RuntimeException ignored) {
            // Metrics are observational and cannot roll back a committed lifecycle transition.
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
