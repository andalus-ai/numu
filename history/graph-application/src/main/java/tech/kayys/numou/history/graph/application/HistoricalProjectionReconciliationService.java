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

/** Compares canonical PostgreSQL events with the rebuildable graph projection. */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionReconciliationStore canonical;
    private final HistoricalProjectionReconciliationPort projection;
    private final HistoricalRelationshipProjectionPort replayPort;

    public HistoricalProjectionReconciliationService(
            HistoricalProjectionReconciliationStore canonical,
            HistoricalProjectionReconciliationPort projection,
            HistoricalRelationshipProjectionPort replayPort) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.replayPort = Objects.requireNonNull(replayPort, "replayPort");
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("pageSize must be 1..500");
        long checked = 0, missing = 0, stale = 0, divergent = 0, orphaned = 0, repaired = 0;
        UUID cursor = null;
        while (true) {
            List<ClaimedProjectionEvent> page = canonical.latestPage(tenantId, cursor, pageSize);
            if (page.isEmpty()) break;
            for (ClaimedProjectionEvent event : page) {
                checked++;
                UUID id = event.aggregateId();
                var state = projection.find(tenantId, id);
                String expectedHash = sha256(event.payload());
                if (state.isEmpty()) {
                    missing++;
                    if (repair) { replayPort.project(event); repaired++; }
                } else if (state.get().revision() < event.aggregateRevision()) {
                    stale++;
                    if (repair) { replayPort.project(event); repaired++; }
                } else if (state.get().revision() > event.aggregateRevision()) {
                    // Never overwrite a graph revision newer than the canonical source.
                    divergent++;
                } else if (!event.eventKey().equals(state.get().eventKey())
                        || !expectedHash.equals(state.get().payloadHash())) {
                    divergent++;
                    if (repair) { replayPort.project(event); repaired++; }
                }
                cursor = id;
            }
            if (page.size() < pageSize) break;
        }

        UUID graphCursor = null;
        while (true) {
            List<UUID> ids = projection.listRelationshipIds(tenantId, graphCursor, pageSize);
            if (ids.isEmpty()) break;
            for (UUID id : ids) {
                if (!canonical.exists(tenantId, id)) orphaned++;
                graphCursor = id;
            }
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
        public boolean clean() { return missing == 0 && stale == 0 && divergent == 0 && orphaned == 0; }
    }
}
