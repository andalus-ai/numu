package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionReconciliationStore;
import java.time.Instant;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionReconciliationServiceTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    @Test void detectsAndRepairsMissingStaleAndSameRevisionDivergentRecords() throws Exception {
        UUID missing=id(1), stale=id(2), divergent=id(3), orphan=id(4);
        var events=List.of(event(missing,1),event(stale,3),event(divergent,2));
        var canonical=new FakeCanonical(events,Set.of(missing,stale,divergent));
        var projection=new FakeProjection();
        projection.states.put(stale,new HistoricalProjectionReconciliationPort.ProjectionState(1,"old","old"));
        projection.states.put(divergent,new HistoricalProjectionReconciliationPort.ProjectionState(2,"wrong","wrong"));
        projection.ids.addAll(List.of(missing,stale,divergent,orphan));
        List<ClaimedProjectionEvent> repaired=new ArrayList<>();
        var report=new HistoricalProjectionReconciliationService(canonical,projection,repaired::add)
                .reconcile(TENANT,2,true);
        assertEquals(3,report.canonicalChecked());
        assertEquals(1,report.missing());
        assertEquals(1,report.stale());
        assertEquals(1,report.divergent());
        assertEquals(1,report.orphaned());
        assertEquals(3,report.repaired());
        assertEquals(Set.of(missing,stale,divergent),
                new HashSet<>(repaired.stream().map(ClaimedProjectionEvent::aggregateId).toList()));
    }

    @Test void newerProjectionRevisionIsNeverOverwritten() throws Exception {
        UUID id=id(10); var event=event(id,1);
        var canonical=new FakeCanonical(List.of(event),Set.of(id));
        var projection=new FakeProjection();
        projection.states.put(id,new HistoricalProjectionReconciliationPort.ProjectionState(2,"newer","hash"));
        List<ClaimedProjectionEvent> repaired=new ArrayList<>();
        var report=new HistoricalProjectionReconciliationService(canonical,projection,repaired::add)
                .reconcile(TENANT,10,true);
        assertEquals(1,report.divergent());
        assertTrue(repaired.isEmpty());
    }

    @Test void refusesCrossTenantOrUnorderedCanonicalPages() {
        UUID id=id(20);
        var wrongTenant=event(id,1);
        var canonical=new FakeCanonical(List.of(new ClaimedProjectionEvent(wrongTenant.eventId(),
                wrongTenant.eventKey(),wrongTenant.eventType(),wrongTenant.schemaVersion(),id(99),
                wrongTenant.aggregateId(),wrongTenant.aggregateRevision(),wrongTenant.payload(),
                wrongTenant.occurredAt(),0,0,UUID.randomUUID(),NOW)),Set.of(id));
        assertThrows(IllegalStateException.class,()->new HistoricalProjectionReconciliationService(
                canonical,new FakeProjection(),e->{}).reconcile(TENANT,10,false));
    }

    private static UUID id(long suffix){return UUID.fromString("00000000-0000-0000-0000-"+String.format("%012d",suffix));}
    private static ClaimedProjectionEvent event(UUID id,long revision){
        String payload="{\"relationshipId\":\""+id+"\",\"revision\":"+revision+"}";
        return new ClaimedProjectionEvent(UUID.randomUUID(),"event-"+id+"-"+revision,
                "history.relationship.revision-appended",1,TENANT,id,revision,payload,NOW,0,0,UUID.randomUUID(),NOW);
    }
    private static final class FakeCanonical implements HistoricalProjectionReconciliationStore {
        final List<ClaimedProjectionEvent> events; final Set<UUID> ids;
        FakeCanonical(List<ClaimedProjectionEvent> events,Set<UUID> ids){
            this.events=events.stream().sorted(Comparator.comparing(e -> e.aggregateId().toString())).toList();
            this.ids=ids;
        }
        public List<ClaimedProjectionEvent> latestPage(UUID tenant,UUID after,int limit){
            return events.stream().filter(e->after==null||e.aggregateId().toString().compareTo(after.toString())>0).limit(limit).toList();
        }
        public boolean exists(UUID tenant,UUID id){return ids.contains(id);}
    }
    private static final class FakeProjection implements HistoricalProjectionReconciliationPort {
        final Map<UUID,ProjectionState> states=new HashMap<>(); final List<UUID> ids=new ArrayList<>();
        public Optional<ProjectionState> find(UUID tenant,UUID id){return Optional.ofNullable(states.get(id));}
        public List<UUID> listRelationshipIds(UUID tenant,UUID after,int limit){
            return ids.stream().filter(id->after==null||id.toString().compareTo(after.toString())>0).sorted().limit(limit).toList();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Test void classifiesMissingStaleAheadAndDivergent() {
        FakeCanonical canonical = new FakeCanonical();
        FakeGraph graph = new FakeGraph();
        canonical.values.put(ID, canonical(ID, 4, "event-4", "person-a"));
        graph.values.put(ID, projected(ID, 3, "event-3", "person-a"));
        var service = service(canonical, graph);
        assertEquals(HistoricalProjectionReconciliationService.Drift.STALE,
                service.auditTenantCanonicalPage(null, 20).findings().getFirst().drift());

        graph.values.put(ID, projected(ID, 5, "event-5", "person-a"));
        assertEquals(HistoricalProjectionReconciliationService.Drift.AHEAD,
                service.auditTenantCanonicalPage(null, 20).findings().getFirst().drift());

        graph.values.put(ID, projected(ID, 4, "event-4", "person-other"));
        assertEquals(HistoricalProjectionReconciliationService.Drift.DIVERGENT,
                service.auditTenantCanonicalPage(null, 20).findings().getFirst().drift());

        graph.values.clear();
        assertEquals(HistoricalProjectionReconciliationService.Drift.MISSING,
                service.auditTenantCanonicalPage(null, 20).findings().getFirst().drift());
    }

    @Test void detectsOrphanedGraphRecord() {
        FakeCanonical canonical = new FakeCanonical();
        FakeGraph graph = new FakeGraph();
        graph.values.put(ID, projected(ID, 1, "orphan", "person-a"));
        var page = service(canonical, graph).auditTenantOrphanPage(null, 20);
        assertEquals(HistoricalProjectionReconciliationService.Drift.ORPHAN, page.findings().getFirst().drift());
    }

    @Test void tenantScopeComesFromContextAndAuthorizationIsRequired() {
        FakeCanonical canonical = new FakeCanonical();
        FakeGraph graph = new FakeGraph();
        var denied = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) { throw new SecurityException("denied"); }
            public void requireCanAuditPublicCorpus() {}
        };
        var service = new HistoricalProjectionReconciliationService(canonical, graph, () -> TENANT, denied);
        assertThrows(SecurityException.class, () -> service.auditTenantCanonicalPage(null, 20));
    }

    private static HistoricalProjectionReconciliationService service(FakeCanonical c, FakeGraph g) {
        var auth = new HistoricalProjectionMaintenanceAuthorizationPort() {
            public void requireCanRebuildTenant(UUID id) {}
            public void requireCanRebuildPublicCorpus() {}
            public void requireCanAuditTenant(UUID id) { assertEquals(TENANT, id); }
            public void requireCanAuditPublicCorpus() {}
        };
        return new HistoricalProjectionReconciliationService(c, g, () -> TENANT, auth);
    }

    private static HistoricalProjectionAuditStore.CanonicalRelationship canonical(UUID id,long rev,String key,String sid) {
        return new HistoricalProjectionAuditStore.CanonicalRelationship(id,rev,key,"founded","PERSON",sid,"COLLECTIVE","group","{}");
    }
    private static HistoricalProjectionInspectionPort.ProjectedRelationship projected(UUID id,long rev,String key,String sid) {
        return new HistoricalProjectionInspectionPort.ProjectedRelationship(id,rev,key,"founded","PERSON",sid,"COLLECTIVE","group");
    }
    private static final class FakeCanonical implements HistoricalProjectionAuditStore {
        Map<UUID,CanonicalRelationship> values=new HashMap<>();
        public List<CanonicalRelationship> nextLatestPage(UUID tenant,String after,int limit) {
            return values.values().stream().sorted(Comparator.comparing(v->v.relationshipId().toString()))
                    .filter(v->after==null||v.relationshipId().toString().compareTo(after)>0).limit(limit).toList();
        }
        public Optional<CanonicalRelationship> findLatest(UUID tenant,UUID id){return Optional.ofNullable(values.get(id));}
    }
    private static final class FakeGraph implements HistoricalProjectionInspectionPort {
        Map<UUID,ProjectedRelationship> values=new HashMap<>();
        public Optional<ProjectedRelationship> find(UUID tenant,UUID id){return Optional.ofNullable(values.get(id));}
        public List<ProjectedRelationship> nextPage(UUID tenant,String after,int limit) {
            return values.values().stream().sorted(Comparator.comparing(v->v.relationshipId().toString()))
                    .filter(v->after==null||v.relationshipId().toString().compareTo(after)>0).limit(limit).toList();
        }
    }
}
