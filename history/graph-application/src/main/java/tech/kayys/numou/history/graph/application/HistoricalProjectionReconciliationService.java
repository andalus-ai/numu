package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRepairAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionRepairAuditStore.RepairOutcome;

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
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final HistoricalProjectionMetricsPort metrics;
    private final HistoricalProjectionOperatorContext operatorContext;
    private final HistoricalProjectionRepairAuditStore repairAudit;
    private final Clock clock;

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization) {
        this(canonical,projection,replayPort,tenantContext,authorization,snapshot -> {});
    }

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionMetricsPort metrics) {
        this(canonical,projection,replayPort,tenantContext,authorization,metrics,null,null,Clock.systemUTC());
    }

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionMetricsPort metrics, HistoricalProjectionOperatorContext operatorContext,
            HistoricalProjectionRepairAuditStore repairAudit, Clock clock) {
        this.canonical=Objects.requireNonNull(canonical,"canonical");
        this.projection=Objects.requireNonNull(projection,"projection");
        this.replayPort=Objects.requireNonNull(replayPort,"replayPort");
        this.tenantContext=Objects.requireNonNull(tenantContext,"tenantContext");
        this.authorization=Objects.requireNonNull(authorization,"authorization");
        this.metrics=Objects.requireNonNull(metrics,"metrics");
        this.operatorContext=operatorContext;
        this.repairAudit=repairAudit;
        this.clock=Objects.requireNonNull(clock,"clock");
    }

    /** Reconciles a staging generation without changing or reading the active projection. */
    public ReconciliationReport reconcileGeneration(UUID tenantId, UUID generationId,
            int pageSize, boolean repair) throws Exception {
        Objects.requireNonNull(generationId, "generationId");
        if (!(projection instanceof GenerationTargetedHistoricalProjectionInspectionPort)) {
            throw new IllegalStateException("inspection provider does not support generation-targeted reads");
        }
        if (repair && !(replayPort instanceof GenerationTargetedHistoricalRelationshipProjectionPort)) {
            throw new IllegalStateException("projection provider does not support generation-targeted repair");
        }
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be 1..200");
        var inspector = (GenerationTargetedHistoricalProjectionInspectionPort) projection;
        long checked=0, missing=0, stale=0, divergent=0, orphaned=0, repaired=0;
        String cursor=null;
        while (true) {
            List<CanonicalRelationship> page=canonical.nextLatestPage(tenantId,cursor,pageSize);
            if(page.isEmpty()) break;
            for(CanonicalRelationship event:page) {
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
                cursor=event.relationshipId().toString();
            }
            if(page.size()<pageSize) break;
        }
        String graphCursor=null;
        while(true) {
            List<HistoricalProjectionInspectionPort.ProjectedRelationship> page=
                    inspector.nextPageInGeneration(tenantId,generationId,graphCursor,pageSize);
            if(page.isEmpty()) break;
            for(var row:page) {
                if(canonical.findLatest(tenantId,row.relationshipId()).isEmpty()) orphaned++;
                graphCursor=row.relationshipId().toString();
            }
            if(page.size()<pageSize) break;
        }
        return new ReconciliationReport(tenantId,checked,missing,stale,divergent,orphaned,repaired);
    }

    private void projectIntoGeneration(CanonicalRelationship event, UUID generationId) throws Exception {
        ((GenerationTargetedHistoricalRelationshipProjectionPort) replayPort)
                .projectIntoGeneration(toProjectionEvent(event), generationId);
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        if(pageSize<1||pageSize>200) throw new IllegalArgumentException("pageSize must be 1..200");
        authorizeScope(tenantId,repair);

        UUID actorId=null;
        UUID operationId=null;
        if(repair) {
            if(operatorContext==null||repairAudit==null)
                throw new IllegalStateException("repair requires an authenticated operator context and durable audit store");
            actorId=Objects.requireNonNull(operatorContext.requireActorId(),"operator context must provide an actor");
            operationId=Objects.requireNonNull(repairAudit.start(tenantId,actorId,pageSize,clock.instant()),
                    "repair audit store must return an operation id");
        }

        try {
            long checked=0,missing=0,stale=0,divergent=0,orphaned=0,repaired=0;
            String cursor=null;
            while(true) {
                List<CanonicalRelationship> page=canonical.nextLatestPage(tenantId,cursor,pageSize);
                if(page.isEmpty()) break;
                String previous=cursor;
                for(CanonicalRelationship event:page) {
                    String current=event.relationshipId().toString();
                    if(previous!=null&&current.compareTo(previous)<=0)
                        throw new IllegalStateException("canonical reconciliation page is not strictly ordered");
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
                                ||!event.relationshipKind().equals(actual.relationshipKind())
                                ||!event.subjectKind().equals(actual.subjectKind())
                                ||!event.subjectId().equals(actual.subjectId())
                                ||!event.objectKind().equals(actual.objectKind())
                                ||!event.objectId().equals(actual.objectId())
                                ||!hash.equals(actual.payloadHash())) {
                            divergent++;
                            if(repair){replayPort.project(toProjectionEvent(event));repaired++;}
                        }
                    }
                    cursor=current;
                    previous=current;
                }
                if(page.size()<pageSize) break;
            }

            String graphCursor=null;
            while(true) {
                List<HistoricalProjectionInspectionPort.ProjectedRelationship> page=
                        projection.nextPage(tenantId,graphCursor,pageSize);
                if(page.isEmpty()) break;
                String previous=graphCursor;
                for(var row:page) {
                    String current=row.relationshipId().toString();
                    if(previous!=null&&current.compareTo(previous)<=0)
                        throw new IllegalStateException("graph reconciliation page is not strictly ordered");
                    if(canonical.findLatest(tenantId,row.relationshipId()).isEmpty()) orphaned++;
                    graphCursor=current;
                    previous=current;
                }
                if(page.size()<pageSize) break;
            }

            ReconciliationReport report=new ReconciliationReport(tenantId,checked,missing,stale,divergent,orphaned,repaired);
            if(repair) repairAudit.complete(tenantId,actorId,operationId,
                    new RepairOutcome(pageSize,checked,missing,stale,divergent,orphaned,repaired),clock.instant());
            try {
                metrics.recordReconciliation(new HistoricalProjectionMetricsPort.ReconciliationMetric(
                        checked,missing,stale,divergent,orphaned,repaired));
            } catch (RuntimeException ignored) {
                // Metrics are best-effort; durable repair audit is the operational record.
            }
            return report;
        } catch(Exception failure) {
            if(repair) {
                try { repairAudit.fail(tenantId,actorId,operationId,pageSize,safeFailure(failure),clock.instant()); }
                catch(RuntimeException auditFailure) { failure.addSuppressed(auditFailure); }
            }
            throw failure;
        }
    }

    private void authorizeScope(UUID tenantId,boolean repair) {
        if(tenantId==null) {
            authorization.requireCanAuditPublicCorpus();
            if(repair) authorization.requireCanRebuildPublicCorpus();
            return;
        }
        UUID trusted=Objects.requireNonNull(tenantContext.requireTenantId(),
                "trusted tenant context must provide a tenant");
        if(!trusted.equals(tenantId)) throw new SecurityException("requested audit scope differs from authenticated tenant");
        authorization.requireCanAuditTenant(trusted);
        if(repair) authorization.requireCanRebuildTenant(trusted);
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
