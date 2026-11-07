package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalProjectionReconciliationServiceTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Test void classifiesMissingStaleAheadAndDivergent() {
        FakeCanonical canonical = new FakeCanonical();
        FakeGraph graph = new FakeGraph();
        canonical.values.put(ID, canonical(ID, 4, "event-4", "person-a"));
        graph.values.put(ID, projected(ID, 3, "event-3", "person-a"));
        var service = new HistoricalProjectionReconciliationService(canonical, graph);
        var page = service.auditCanonicalPage(TENANT, null, 20);
        assertEquals(HistoricalProjectionReconciliationService.Drift.STALE, page.findings().getFirst().drift());

        graph.values.put(ID, projected(ID, 5, "event-5", "person-a"));
        assertEquals(HistoricalProjectionReconciliationService.Drift.AHEAD,
                service.auditCanonicalPage(TENANT, null, 20).findings().getFirst().drift());

        graph.values.put(ID, projected(ID, 4, "event-4", "person-other"));
        assertEquals(HistoricalProjectionReconciliationService.Drift.DIVERGENT,
                service.auditCanonicalPage(TENANT, null, 20).findings().getFirst().drift());

        graph.values.clear();
        assertEquals(HistoricalProjectionReconciliationService.Drift.MISSING,
                service.auditCanonicalPage(TENANT, null, 20).findings().getFirst().drift());
    }

    @Test void detectsOrphanedGraphRecord() {
        FakeCanonical canonical = new FakeCanonical();
        FakeGraph graph = new FakeGraph();
        graph.values.put(ID, projected(ID, 1, "orphan", "person-a"));
        var page = new HistoricalProjectionReconciliationService(canonical, graph)
                .auditOrphanPage(TENANT, null, 20);
        assertEquals(HistoricalProjectionReconciliationService.Drift.ORPHAN, page.findings().getFirst().drift());
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
