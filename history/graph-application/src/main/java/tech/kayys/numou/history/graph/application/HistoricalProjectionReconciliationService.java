package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionAuditStore.CanonicalRelationship;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, read-only drift audit. It never mutates canonical records or the graph.
 * Callers must supply tenant scope from a trusted maintenance/tenant context.
 */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionAuditStore canonical;
    private final HistoricalProjectionInspectionPort projection;

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
                                                       HistoricalProjectionInspectionPort projection) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    public AuditPage auditCanonicalPage(UUID tenantId, String afterRelationshipId, int limit) {
        requireLimit(limit);
        List<CanonicalRelationship> source = canonical.nextLatestPage(tenantId, afterRelationshipId, limit);
        List<Finding> findings = new ArrayList<>();
        for (CanonicalRelationship expected : source) {
            var actual = projection.find(tenantId, expected.relationshipId());
            if (actual.isEmpty()) {
                findings.add(new Finding(expected.relationshipId(), Drift.MISSING, 0, expected.revision()));
                continue;
            }
            var value = actual.get();
            Drift drift = classify(expected, value);
            if (drift != Drift.HEALTHY) {
                findings.add(new Finding(expected.relationshipId(), drift, value.revision(), expected.revision()));
            }
        }
        String next = source.isEmpty() ? afterRelationshipId
                : source.get(source.size() - 1).relationshipId().toString();
        return new AuditPage(List.copyOf(findings), source.size(), next, source.size() < limit);
    }

    /** Finds graph records that have no canonical event in this exact scope. */
    public AuditPage auditOrphanPage(UUID tenantId, String afterRelationshipId, int limit) {
        requireLimit(limit);
        List<HistoricalProjectionInspectionPort.ProjectedRelationship> graph =
                projection.nextPage(tenantId, afterRelationshipId, limit);
        List<Finding> findings = new ArrayList<>();
        for (var actual : graph) {
            if (canonical.findLatest(tenantId, actual.relationshipId()).isEmpty()) {
                findings.add(new Finding(actual.relationshipId(), Drift.ORPHAN,
                        actual.revision(), 0));
            }
        }
        String next = graph.isEmpty() ? afterRelationshipId
                : graph.get(graph.size() - 1).relationshipId().toString();
        return new AuditPage(List.copyOf(findings), graph.size(), next, graph.size() < limit);
    }

    private static Drift classify(CanonicalRelationship expected,
                                  HistoricalProjectionInspectionPort.ProjectedRelationship actual) {
        if (actual.revision() < expected.revision()) return Drift.STALE;
        if (actual.revision() > expected.revision()) return Drift.AHEAD;
        boolean same = Objects.equals(expected.eventKey(), actual.eventKey())
                && Objects.equals(expected.relationshipKind(), actual.relationshipKind())
                && Objects.equals(expected.subjectKind(), actual.subjectKind())
                && Objects.equals(expected.subjectId(), actual.subjectId())
                && Objects.equals(expected.objectKind(), actual.objectKind())
                && Objects.equals(expected.objectId(), actual.objectId());
        return same ? Drift.HEALTHY : Drift.DIVERGENT;
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }

    public enum Drift { HEALTHY, MISSING, STALE, AHEAD, DIVERGENT, ORPHAN }
    public record Finding(UUID relationshipId, Drift drift, long projectedRevision, long canonicalRevision) {}
    public record AuditPage(List<Finding> findings, int scanned, String nextCursor, boolean endOfScan) {}
}
