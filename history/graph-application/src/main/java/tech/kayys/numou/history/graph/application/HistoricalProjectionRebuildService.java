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
 * A page cursor advances only after every event in that page has been projected.
 * Tenant scopes are checked against trusted server-side context; public rebuilds
 * require their separate maintenance permission.
 */
public final class HistoricalProjectionRebuildService {
    private final HistoricalProjectionRebuildStore store;
    private final HistoricalRelationshipProjectionPort projection;
    private final Clock clock;
    private final int pageSize;
    private final HistoricalProjectionMetricsPort metrics;

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipProjectionPort projection,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            Clock clock, int pageSize) {
        this(store,projection,tenantContext,authorization,clock,pageSize,snapshot -> {});
    }

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
            HistoricalRelationshipProjectionPort projection,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            Clock clock, int pageSize, HistoricalProjectionMetricsPort metrics) {
        this.store = Objects.requireNonNull(store, "store");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        this.pageSize = pageSize;
    }

    public RebuildRun start(UUID tenantId) {
        authorizeScope(tenantId);
        return store.start(tenantId, clock.instant());
    }

    /** Processes at most one page; repeat with the same run ID to resume safely. */
    public RebuildProgress resumeOnePage(UUID tenantId, UUID runId) {
        return resumeOnePageInternal(tenantId, runId, null);
    }

    /** Replays one page into an isolated staging generation, leaving active data untouched. */
    public RebuildProgress resumeOnePage(UUID tenantId, UUID runId, UUID generationId) {
        Objects.requireNonNull(generationId, "generationId");
        if (!(projection instanceof GenerationTargetedHistoricalRelationshipProjectionPort)) {
            throw new IllegalStateException("projection provider does not support generation-targeted replay");
        }
        return resumeOnePageInternal(tenantId, runId, generationId);
    }

    private RebuildProgress resumeOnePageInternal(UUID tenantId, UUID runId, UUID generationId) {
        RebuildRun run = store.find(tenantId, runId)
                .orElseThrow(() -> new IllegalArgumentException("rebuild run does not exist in this scope"));
        if (run.status() == RebuildRun.Status.COMPLETED) {
            return progress(tenantId, run, 0, true);
        }

        List<ReplayEvent> page = store.nextPage(tenantId, runId, pageSize);
        if (page.isEmpty()) {
            if (!store.complete(tenantId, runId, run.cursorSequence(), clock.instant())) {
                RebuildRun latest = store.find(tenantId, runId)
                        .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
                return progress(tenantId, latest, 0, latest.status() == RebuildRun.Status.COMPLETED);
            }
            RebuildRun completed = store.find(tenantId, runId).orElseThrow();
            return progress(tenantId, completed, 0, true);
        }

        long nextCursor = run.cursorSequence();
        long previousSequence = run.cursorSequence();
        for (ReplayEvent event : page) {
            if (!Objects.equals(tenantId, event.tenantId())) {
                throw new IllegalStateException("rebuild page crossed tenant scope");
            }
            if (event.sequence() <= previousSequence || event.sequence() > run.highWaterSequence()) {
                throw new IllegalStateException("rebuild page contains an unordered or out-of-snapshot event");
            }
            try {
                ClaimedProjectionEvent claimed = asClaimedEvent(event);
                if (generationId == null) {
                    projection.project(claimed);
                } else {
                    ((GenerationTargetedHistoricalRelationshipProjectionPort) projection)
                            .projectIntoGeneration(claimed, generationId);
                }
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to project historical relationship event " + event.eventKey(), failure);
            }
            previousSequence = event.sequence();
            nextCursor = event.sequence();
        }
        if (!store.advance(tenantId, runId, run.cursorSequence(), nextCursor, clock.instant())) {
            RebuildRun latest = store.find(tenantId, runId)
                    .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
            return progress(tenantId, latest, page.size(), latest.status() == RebuildRun.Status.COMPLETED);
        }
        RebuildRun advanced = store.find(tenantId, runId).orElseThrow();
        return progress(tenantId, advanced, page.size(), false);
    }

    private RebuildProgress progress(UUID tenantId, RebuildRun run, int projected, boolean completed) {
        try {
            metrics.recordRebuildProgress(new HistoricalProjectionMetricsPort.RebuildMetric(
                    projected,run.cursorSequence(),run.highWaterSequence(),completed));
        } catch (RuntimeException ignored) {
            // Telemetry must never turn a committed projection checkpoint into a failed page.
        }
        return new RebuildProgress(run,projected,completed);
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
