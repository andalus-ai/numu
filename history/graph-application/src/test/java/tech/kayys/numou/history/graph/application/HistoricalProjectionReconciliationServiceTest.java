package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionReconciliationServiceTest {
    private static final UUID TENANT=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW=Instant.parse("2026-11-07T00:00:00Z");
    @Test void reportsAndRepairsMissingStaleAndDivergentRowsAndCountsOrphans() throws Exception {
        UUID missing=UUID.randomUUID(),stale=UUID.randomUUID(),divergent=UUID.randomUUID(),orphan=UUID.randomUUID();
        FakeAudit audit=new FakeAudit(List.of(row(missing,1),row(stale,3),row(divergent,2)));
        FakeInspection graph=new FakeInspection();
        graph.rows.put(stale,projected(stale,1,"old"));
        graph.rows.put(divergent,projected(divergent,2,"wrong"));
        graph.rows.put(orphan,projected(orphan,1,"orphan"));
        List<ClaimedProjectionEvent> repairs=new ArrayList<>();
        var report=new HistoricalProjectionReconciliationService(audit,graph,repairs::add,()->TENANT,allowAll()).reconcile(TENANT,2,true);
        assertEquals(3,report.canonicalChecked());assertEquals(1,report.missing());assertEquals(1,report.stale());
        assertEquals(1,report.divergent());assertEquals(1,report.orphaned());assertEquals(3,report.repaired());
        assertEquals(Set.of(missing,stale,divergent),new HashSet<>(repairs.stream().map(ClaimedProjectionEvent::aggregateId).toList()));
    }
    @Test void neverOverwritesGraphRevisionNewerThanCanonical() throws Exception {
        UUID id=UUID.randomUUID();FakeAudit audit=new FakeAudit(List.of(row(id,1)));FakeInspection graph=new FakeInspection();
        graph.rows.put(id,projected(id,2,"newer"));List<ClaimedProjectionEvent> repairs=new ArrayList<>();
        var report=new HistoricalProjectionReconciliationService(audit,graph,repairs::add,()->TENANT,allowAll()).reconcile(TENANT,10,true);
        assertEquals(1,report.divergent());assertTrue(repairs.isEmpty());
    }

    private static HistoricalProjectionMaintenanceAuthorizationPort allowAll(){
        return new HistoricalProjectionMaintenanceAuthorizationPort(){
            public void requireCanRebuildTenant(UUID id){}
            public void requireCanRebuildPublicCorpus(){}
            public void requireCanAuditTenant(UUID id){}
            public void requireCanAuditPublicCorpus(){}
        };
    }

    private static CanonicalRelationship row(UUID id,long rev) {
        String payload="{\"relationshipId\":\""+id+"\",\"revision\":"+rev+"}";
        return new CanonicalRelationship(UUID.randomUUID(),"history.relationship.revision-appended",1,TENANT,id,rev,
                "event-"+id+"-"+rev,"founded","PERSON","p-"+id,"COLLECTIVE","c-"+id,payload,NOW);
    }
    private static HistoricalProjectionInspectionPort.ProjectedRelationship projected(UUID id,long rev,String key) {
        return new HistoricalProjectionInspectionPort.ProjectedRelationship(id,rev,key,"founded","PERSON","p-"+id,
                "COLLECTIVE","c-"+id,"old");
    }
    private static final class FakeAudit implements HistoricalProjectionAuditStore {
        final List<CanonicalRelationship> rows;
        FakeAudit(List<CanonicalRelationship> rows){this.rows=rows.stream().sorted(Comparator.comparing(r->r.relationshipId().toString())).toList();}
        public List<CanonicalRelationship> nextLatestPage(UUID tenant,String after,int limit){
            return rows.stream().filter(r->after==null||r.relationshipId().toString().compareTo(after)>0).limit(limit).toList();
        }
        public Optional<CanonicalRelationship> findLatest(UUID tenant,UUID id){return rows.stream().filter(r->r.relationshipId().equals(id)).findFirst();}
    }
    private static final class FakeInspection implements HistoricalProjectionInspectionPort {
        final Map<UUID,ProjectedRelationship> rows=new HashMap<>();
        public Optional<ProjectedRelationship> find(UUID tenant,UUID id){return Optional.ofNullable(rows.get(id));}
        public List<ProjectedRelationship> nextPage(UUID tenant,String after,int limit){
            return rows.values().stream().filter(r->after==null||r.relationshipId().toString().compareTo(after)>0)
                    .sorted(Comparator.comparing(r->r.relationshipId().toString())).limit(limit).toList();
        }
    }
}
