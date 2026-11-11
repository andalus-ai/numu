package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Compares the current graph projection with canonical PostgreSQL outbox events. */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionAuditStore canonical;
    private final HistoricalProjectionInspectionPort projection;
    private final HistoricalRelationshipProjectionPort replayPort;
    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.replayPort = Objects.requireNonNull(replayPort, "replayPort");
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be 1..200");
        long checked=0, missing=0, stale=0, divergent=0, orphaned=0, repaired=0;
        String cursor=null;
        while (true) {
            List<CanonicalRelationship> page=canonical.nextLatestPage(tenantId,cursor,pageSize);
            if(page.isEmpty()) break;
            for(CanonicalRelationship event:page) {
                checked++;
                var graph=projection.find(tenantId,event.relationshipId());
                String hash=sha256(event.payload());
                if(graph.isEmpty()) {
                    missing++;
                    if(repair){replayPort.project(toProjectionEvent(event));repaired++;}
                } else {
                    var actual=graph.get();
                    if(actual.revision()<event.revision()) {
                        stale++;
                        if(repair){replayPort.project(toProjectionEvent(event));repaired++;}
                    } else if(actual.revision()>event.revision()) {
                        divergent++;
                    } else if(!event.eventKey().equals(actual.eventKey())
                            || !event.relationshipKind().equals(actual.relationshipKind())
                            || !event.subjectKind().equals(actual.subjectKind())
                            || !event.subjectId().equals(actual.subjectId())
                            || !event.objectKind().equals(actual.objectKind())
                            || !event.objectId().equals(actual.objectId())
                            || !hash.equals(actual.payloadHash())) {
                        divergent++;
                        if(repair){replayPort.project(toProjectionEvent(event));repaired++;}
                    }
                }
                cursor=event.relationshipId().toString();
            }
            if(page.size()<pageSize) break;
        }
        String graphCursor=null;
        while(true) {
            List<HistoricalProjectionInspectionPort.ProjectedRelationship> page=
                    projection.nextPage(tenantId,graphCursor,pageSize);
            if(page.isEmpty()) break;
            for(var row:page) {
                if(canonical.findLatest(tenantId,row.relationshipId()).isEmpty()) orphaned++;
                graphCursor=row.relationshipId().toString();
            }
            if(page.size()<pageSize) break;
        }
        return new ReconciliationReport(tenantId,checked,missing,stale,divergent,orphaned,repaired);
    }

    private static ClaimedProjectionEvent toProjectionEvent(CanonicalRelationship e) {
        UUID token=UUID.nameUUIDFromBytes(e.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(e.eventId(),e.eventKey(),e.eventType(),e.schemaVersion(),e.tenantId(),
                e.relationshipId(),e.revision(),e.payload(),e.occurredAt(),0,0,token,java.time.Instant.MAX);
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
    }
    public record ReconciliationReport(UUID tenantId,long canonicalChecked,long missing,long stale,
            long divergent,long orphaned,long repaired) {
        public boolean clean(){return missing==0&&stale==0&&divergent==0&&orphaned==0;}
    }
}
