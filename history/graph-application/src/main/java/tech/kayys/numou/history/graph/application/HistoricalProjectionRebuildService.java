package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.RebuildRun;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRebuildStore.ReplayEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resumable, bounded replay of one public or tenant-private projection scope.
 * A page cursor advances only after every event has been projected. Both cursor
 * and lifecycle version fence the checkpoint against concurrent workers and
 * pause/cancel/resume operations.
 */
public final class HistoricalProjectionRebuildService {
    private final HistoricalProjectionRebuildStore store;
    private final HistoricalRelationshipProjectionPort projection;
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final Clock clock;
    private final int pageSize;
    private final HistoricalProjectionMetricsPort metrics;
    private final UUID generationId;
    private final HistoricalProjectionRebuildFailurePort failurePort;

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipProjectionPort projection,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            Clock clock, int pageSize, HistoricalProjectionMetricsPort metrics) {
        this(store, projection, tenantContext, authorization, clock, pageSize, metrics, null, null);
    }

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipProjectionPort projection,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            Clock clock, int pageSize, HistoricalProjectionMetricsPort metrics, UUID generationId) {
        this(store, projection, tenantContext, authorization, clock, pageSize, metrics, generationId, null);
    }

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipProjectionPort projection,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            Clock clock, int pageSize, HistoricalProjectionMetricsPort metrics,
            UUID generationId, HistoricalProjectionRebuildFailurePort failurePort) {
        this.store = Objects.requireNonNull(store, "store");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.generationId = generationId;
        this.failurePort = failurePort;
        if (generationId != null && !(projection instanceof GenerationTargetedHistoricalRelationshipProjectionPort)) {
            throw new IllegalArgumentException("generation-targeted rebuild requires a generation-aware projection adapter");
        }
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        this.pageSize = pageSize;
    }

    public RebuildRun start(UUID tenantId) {
        authorizeScope(tenantId);
        return store.start(tenantId, clock.instant());
    }

    /** Processes at most one page. A paused or terminal run is never replayed. */
    public RebuildProgress resumeOnePage(UUID tenantId, UUID runId) {
        Objects.requireNonNull(runId, "runId");
        authorizeScope(tenantId);
        RebuildRun run = store.find(tenantId, runId)
                .orElseThrow(() -> new IllegalArgumentException("rebuild run does not exist in this scope"));

        if (run.status() != RebuildRun.Status.RUNNING) {
            return progress(run, 0, run.status() == RebuildRun.Status.COMPLETED);
        }

        List<ReplayEvent> page = store.nextPage(tenantId, runId, pageSize);
        validatePage(tenantId, run, page);
        if (page.isEmpty()) {
            boolean completed = store.completeFenced(tenantId, runId, run.cursorSequence(),
                    run.lifecycleVersion(), clock.instant());
            RebuildRun latest = store.find(tenantId, runId)
                    .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
            return progress(latest, 0, completed || latest.status() == RebuildRun.Status.COMPLETED);
        }

        for (ReplayEvent event : page) {
            try {
                ClaimedProjectionEvent claimed = asClaimedEvent(event);
                if (generationId == null) {
                    projection.project(claimed);
                } else {
                    ((GenerationTargetedHistoricalRelationshipProjectionPort) projection)
                            .projectIntoGeneration(claimed, generationId);
                }
            } catch (Exception failure) {
                String detail = "Unable to project historical relationship event " + event.eventKey()
                        + ": " + Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
                if (failurePort != null) {
                    try {
                        failurePort.markFailed(tenantId, runId, detail);
                    } catch (RuntimeException auditOrTransitionFailure) {
                        failure.addSuppressed(auditOrTransitionFailure);
                    }
                }
                if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                throw new IllegalStateException(detail, failure);
            }
        }

        long nextCursor = page.get(page.size() - 1).sequence();
        boolean advanced = store.advanceFenced(tenantId, runId, run.cursorSequence(),
                nextCursor, run.lifecycleVersion(), clock.instant());
        RebuildRun latest = store.find(tenantId, runId)
                .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
        // A lost fence means another worker or operator changed the run. Projection
        // calls are required to be idempotent, so a later RUNNING worker can replay.
        return progress(latest, advanced ? page.size() : 0,
                latest.status() == RebuildRun.Status.COMPLETED);
    }

    private void validatePage(UUID tenantId, RebuildRun run, List<ReplayEvent> page) {
        long previousSequence = run.cursorSequence();
        for (ReplayEvent event : page) {
            if (!Objects.equals(tenantId, event.tenantId())) {
                throw new IllegalStateException("rebuild page crossed tenant scope");
            }
            if (event.sequence() <= previousSequence || event.sequence() > run.highWaterSequence()) {
                throw new IllegalStateException("rebuild page contains an unordered or out-of-snapshot event");
            }
            previousSequence = event.sequence();
        }
    }

    private RebuildProgress progress(RebuildRun run, int projected, boolean completed) {
        try {
            metrics.recordRebuildProgress(new HistoricalProjectionMetricsPort.RebuildMetric(
                    projected, run.cursorSequence(), run.highWaterSequence(), completed));
        } catch (RuntimeException ignored) {
            // Telemetry must never turn a committed projection checkpoint into a failed page.
        }
        return new RebuildProgress(run, projected, completed);
    }

    private void authorizeScope(UUID tenantId) {
        if (tenantId == null) {
            authorization.requireCanRebuildPublicCorpus();
            return;
        }
        UUID trustedTenant = Objects.requireNonNull(tenantContext.requireTenantId(),
                "trusted tenant context must provide a tenant");
        if (!trustedTenant.equals(tenantId)) {
            throw new SecurityException("requested rebuild scope differs from authenticated tenant");
        }
        authorization.requireCanRebuildTenant(trustedTenant);
    }

    private static ClaimedProjectionEvent asClaimedEvent(ReplayEvent event) {
        UUID token = UUID.nameUUIDFromBytes(event.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(event.eventId(), event.eventKey(), event.eventType(),
                event.schemaVersion(), event.tenantId(), event.aggregateId(), event.aggregateRevision(),
                event.payload(), event.occurredAt(), 0, 0, token, Instant.MAX);
    }

    public record RebuildProgress(RebuildRun run, int projectedEvents, boolean completed) {}
}
