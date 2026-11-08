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
 * Scope is resolved/authorized server-side; callers cannot nominate another tenant.
 */
public final class HistoricalProjectionRebuildService {
    private final HistoricalProjectionRebuildStore store;
    private final HistoricalRelationshipProjectionPort projection;
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final Clock clock;
    private final int pageSize;

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
                                              HistoricalRelationshipProjectionPort projection,
                                              HistoricalRelationshipTenantContext tenantContext,
                                              HistoricalProjectionMaintenanceAuthorizationPort authorization,
                                              Clock clock, int pageSize) {
        this.store = Objects.requireNonNull(store, "store");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        this.pageSize = pageSize;
    }

    public RebuildRun startTenantRebuild() {
        UUID tenantId = requireTenant();
        authorization.requireCanRebuildTenant(tenantId);
        return store.start(tenantId, clock.instant());
    }

    public RebuildRun startPublicCorpusRebuild() {
        authorization.requireCanRebuildPublicCorpus();
        return store.start(null, clock.instant());
    }

    public RebuildProgress resumeTenantPage(UUID runId) {
        UUID tenantId = requireTenant();
        authorization.requireCanRebuildTenant(tenantId);
        return resume(tenantId, runId);
    }

    public RebuildProgress resumePublicCorpusPage(UUID runId) {
        authorization.requireCanRebuildPublicCorpus();
        return resume(null, runId);
    }

    private RebuildProgress resume(UUID tenantId, UUID runId) {
        RebuildRun run = store.find(tenantId, Objects.requireNonNull(runId, "runId"))
                .orElseThrow(() -> new IllegalArgumentException("rebuild run does not exist in this scope"));
        if (run.status() == RebuildRun.Status.COMPLETED) return new RebuildProgress(run, 0, true);

        List<ReplayEvent> page = store.nextPage(tenantId, runId, pageSize);
        if (page.isEmpty()) {
            if (!store.complete(tenantId, runId, run.cursorSequence(), clock.instant())) {
                RebuildRun latest = store.find(tenantId, runId)
                        .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
                return new RebuildProgress(latest, 0, latest.status() == RebuildRun.Status.COMPLETED);
            }
            return new RebuildProgress(store.find(tenantId, runId).orElseThrow(), 0, true);
        }

        long nextCursor = run.cursorSequence();
        for (ReplayEvent event : page) {
            if (!Objects.equals(tenantId, event.tenantId())) {
                throw new IllegalStateException("rebuild page crossed tenant scope");
            }
            try {
                projection.project(asClaimedEvent(event));
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to project historical relationship event " + event.eventKey(), failure);
            }
            nextCursor = Math.max(nextCursor, event.sequence());
        }
        if (!store.advance(tenantId, runId, run.cursorSequence(), nextCursor, clock.instant())) {
            RebuildRun latest = store.find(tenantId, runId)
                    .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
            return new RebuildProgress(latest, page.size(), latest.status() == RebuildRun.Status.COMPLETED);
        }
        return new RebuildProgress(store.find(tenantId, runId).orElseThrow(), page.size(), false);
    }

    private UUID requireTenant() {
        return Objects.requireNonNull(tenantContext.requireTenantId(),
                "authenticated tenant context did not provide a tenant ID");
    }

    private static ClaimedProjectionEvent asClaimedEvent(ReplayEvent event) {
        UUID token = UUID.nameUUIDFromBytes(event.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(event.eventId(), event.eventKey(), event.eventType(),
                event.schemaVersion(), event.tenantId(), event.aggregateId(), event.aggregateRevision(),
                event.payload(), event.occurredAt(), 0, 0, token, Instant.MAX);
    }

    public record RebuildProgress(RebuildRun run, int projectedEvents, boolean completed) {}
}
