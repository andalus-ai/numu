package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionReconciliationStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compares Neo4j with the latest canonical PostgreSQL outbox event for each
 * relationship. Repairs only replay immutable canonical events.
 */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionReconciliationStore canonical;
    private final HistoricalProjectionReconciliationPort projection;
    private final HistoricalRelationshipProjectionPort replay;

    public HistoricalProjectionReconciliationService(
            HistoricalProjectionReconciliationStore canonical,
            HistoricalProjectionReconciliationPort projection,
            HistoricalRelationshipProjectionPort replay) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.replay = Objects.requireNonNull(replay, "replay");
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        long checked = 0, missing = 0, stale = 0, divergent = 0, orphaned = 0, repaired = 0;
        UUID cursor = null;
        while (true) {
            List<ClaimedProjectionEvent> page = canonical.latestPage(tenantId, cursor, pageSize);
            if (page.isEmpty()) break;
            UUID previous = cursor;
            for (ClaimedProjectionEvent event : page) {
                UUID id = event.aggregateId();
                if (!Objects.equals(tenantId, event.tenantId())) {
                    throw new IllegalStateException("canonical page crossed tenant scope");
                }
                if (previous != null && id.toString().compareTo(previous.toString()) <= 0) {
                    throw new IllegalStateException("canonical page is not strictly ordered");
                }
                checked++;
                var state = projection.find(tenantId, id);
                String hash = sha256(event.payload());
                if (state.isEmpty()) {
                    missing++;
                    if (repair) { replay.project(event); repaired++; }
                } else if (state.get().revision() < event.aggregateRevision()) {
                    stale++;
                    if (repair) { replay.project(event); repaired++; }
                } else if (state.get().revision() > event.aggregateRevision()) {
                    // Newer graph data than the source is a hard divergence. Never
                    // overwrite it automatically; investigate scope or source loss.
                    divergent++;
                } else if (!event.eventKey().equals(state.get().eventKey())
                        || !hash.equals(state.get().payloadHash())) {
                    divergent++;
                    if (repair) { replay.project(event); repaired++; }
                }
                previous = id;
            }
            cursor = previous;
            if (page.size() < pageSize) break;
        }

        UUID graphCursor = null;
        while (true) {
            List<UUID> ids = projection.listRelationshipIds(tenantId, graphCursor, pageSize);
            if (ids.isEmpty()) break;
            UUID previous = graphCursor;
            for (UUID id : ids) {
                if (previous != null && id.compareTo(previous) <= 0) {
                    throw new IllegalStateException("projection page is not strictly ordered");
                }
                if (!canonical.exists(tenantId, id)) orphaned++;
                previous = id;
            }
            graphCursor = previous;
            if (ids.size() < pageSize) break;
        }
        return new ReconciliationReport(tenantId, checked, missing, stale, divergent, orphaned, repaired);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record ReconciliationReport(UUID tenantId, long canonicalChecked, long missing,
            long stale, long divergent, long orphaned, long repaired) {
        public boolean clean() {
            return missing == 0 && stale == 0 && divergent == 0 && orphaned == 0;
        }
    }
}
