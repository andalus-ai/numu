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
 */
public final class HistoricalProjectionRebuildService {
    private final HistoricalProjectionRebuildStore store;
    private final HistoricalRelationshipProjectionPort projection;
    private final Clock clock;
    private final int pageSize;

    public HistoricalProjectionRebuildService(HistoricalProjectionRebuildStore store,
                                              HistoricalRelationshipProjectionPort projection,
                                              Clock clock, int pageSize) {
        this.store = Objects.requireNonNull(store, "store");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        this.pageSize = pageSize;
    }

    public RebuildRun start(UUID tenantId) {
        return store.start(tenantId, clock.instant());
    }

    /**
     * Processes at most one page. Call repeatedly with the same run ID to resume.
     * Replaying a page after a crash is safe because providers reject stale revisions.
     */
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
            return new RebuildProgress(run, 0, true);
        }

        List<ReplayEvent> page = store.nextPage(tenantId, runId, pageSize);
        if (page.isEmpty()) {
            if (!store.complete(tenantId, runId, run.cursorSequence(), clock.instant())) {
                RebuildRun latest = store.find(tenantId, runId)
                        .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
                return new RebuildProgress(latest, 0, latest.status() == RebuildRun.Status.COMPLETED);
            }
            RebuildRun completed = store.find(tenantId, runId).orElseThrow();
            return new RebuildProgress(completed, 0, true);
        }

        long nextCursor = run.cursorSequence();
        for (ReplayEvent event : page) {
            if (!Objects.equals(tenantId, event.tenantId())) {
                throw new IllegalStateException("rebuild page crossed tenant scope");
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
            nextCursor = Math.max(nextCursor, event.sequence());
        }
        if (!store.advance(tenantId, runId, run.cursorSequence(), nextCursor, clock.instant())) {
            RebuildRun latest = store.find(tenantId, runId)
                    .orElseThrow(() -> new IllegalStateException("rebuild run disappeared"));
            return new RebuildProgress(latest, page.size(), latest.status() == RebuildRun.Status.COMPLETED);
        }
        RebuildRun advanced = store.find(tenantId, runId).orElseThrow();
        return new RebuildProgress(advanced, page.size(), false);
    }

    private static ClaimedProjectionEvent asClaimedEvent(ReplayEvent event) {
        UUID token = UUID.nameUUIDFromBytes(event.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(event.eventId(), event.eventKey(), event.eventType(),
                event.schemaVersion(), event.tenantId(), event.aggregateId(), event.aggregateRevision(),
                event.payload(), event.occurredAt(), 0, 0, token, Instant.MAX);
    }

    public record RebuildProgress(RebuildRun run, int projectedEvents, boolean completed) {}
}
