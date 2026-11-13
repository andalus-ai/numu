package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, scope-authorized comparison of the disposable graph projection
 * against the canonical PostgreSQL event history.
 */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionAuditStore canonical;
    private final HistoricalProjectionInspectionPort projection;
    private final HistoricalRelationshipProjectionPort replayPort;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalProjectionMaintenanceAuthorizationPort authorization) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.replayPort = Objects.requireNonNull(replayPort, "replayPort");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        requireCanAudit(tenantId);
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be 1..200");
        authorizeScope(tenantId, repair);

        long checked=0, missing=0, stale=0, divergent=0, orphaned=0, repaired=0;
        String cursor=null;
        while (true) {
            List<CanonicalRelationship> page=canonical.nextLatestPage(tenantId,cursor,pageSize);
            if(page.isEmpty()) break;
            String previous = cursor;
            for(CanonicalRelationship event:page) {
                if (!Objects.equals(tenantId, event.tenantId())) {
                    throw new IllegalStateException("canonical audit page crossed tenant scope");
                }
                String currentId = event.relationshipId().toString();
                if (previous != null && currentId.compareTo(previous) <= 0) {
                    throw new IllegalStateException("canonical audit page is not strictly ordered");
                }
                checked++;
                var graph=inspector.findInGeneration(tenantId,generationId,event.relationshipId());
                String hash=sha256(event.payload());
                if(graph.isEmpty()) {
                    missing++;
                    if(repair){projectIntoGeneration(event,generationId);repaired++;}
                } else {
                    var actual=graph.get();
                    if(actual.revision()<event.revision()) {
                        stale++;
                        if(repair){projectIntoGeneration(event,generationId);repaired++;}
                    } else if(actual.revision()>event.revision()) {
                        // A graph revision newer than the source is never overwritten automatically.
                        divergent++;
                    } else if(!event.eventKey().equals(actual.eventKey())
                            || !event.relationshipKind().equals(actual.relationshipKind())
                            || !event.subjectKind().equals(actual.subjectKind())
                            || !event.subjectId().equals(actual.subjectId())
                            || !event.objectKind().equals(actual.objectKind())
                            || !event.objectId().equals(actual.objectId())
                            || !hash.equals(actual.payloadHash())) {
                        divergent++;
                        if(repair){projectIntoGeneration(event,generationId);repaired++;}
                    }
                }
                previous=currentId;
            }
            cursor=previous;
            if(page.size()<pageSize) break;
        }
        String graphCursor=null;
        while(true) {
            List<HistoricalProjectionInspectionPort.ProjectedRelationship> page=
                    inspector.nextPageInGeneration(tenantId,generationId,graphCursor,pageSize);
            if(page.isEmpty()) break;
            String previous = graphCursor;
            for(var row:page) {
                String currentId = row.relationshipId().toString();
                if (previous != null && currentId.compareTo(previous) <= 0) {
                    throw new IllegalStateException("graph inspection page is not strictly ordered");
                }
                if(canonical.findLatest(tenantId,row.relationshipId()).isEmpty()) orphaned++;
                previous=currentId;
            }
            graphCursor=previous;
            if(page.size()<pageSize) break;
        }
        return new ReconciliationReport(tenantId,checked,missing,stale,divergent,orphaned,repaired);
    }

    private void requireCanAudit(UUID tenantId) {
        if (tenantId == null) authorization.requireCanAuditPublicCorpus();
        else authorization.requireCanAuditTenant(tenantId);
    }

    private static ClaimedProjectionEvent toProjectionEvent(CanonicalRelationship e) {
        UUID token=UUID.nameUUIDFromBytes(e.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(e.eventId(),e.eventKey(),e.eventType(),e.schemaVersion(),e.tenantId(),
                e.relationshipId(),e.revision(),e.payload(),e.occurredAt(),0,0,token,java.time.Instant.MAX);
    }
    private static String sha256(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 is unavailable",impossible);}
    }
    private static String safeFailure(Exception failure) {
        String message=Objects.toString(failure.getMessage(),failure.getClass().getSimpleName()).replaceAll("[\\r\\n\\t]+"," ");
        return message.length()>1000?message.substring(0,1000):message;
    }
    public record ReconciliationReport(UUID tenantId,long canonicalChecked,long missing,long stale,
            long divergent,long orphaned,long repaired) {
        public boolean clean(){return missing==0&&stale==0&&divergent==0&&orphaned==0;}
    }
}
