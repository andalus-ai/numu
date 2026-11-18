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

/** Bounded, scope-authorized comparison of graph projection with canonical PostgreSQL events. */
public final class HistoricalProjectionReconciliationService {
    private final HistoricalProjectionAuditStore canonical;
    private final HistoricalProjectionInspectionPort projection;
    private final HistoricalRelationshipProjectionPort replayPort;
    private final HistoricalProjectionMaintenanceAuthorizationPort authorization;
    private final HistoricalProjectionRepairAuditStore repairAudit;
    private final HistoricalProjectionOperatorContext operatorContext;
    private final Clock clock;

    /** Read-only compatible constructor. Repair requests fail closed without durable audit wiring. */
    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalProjectionMaintenanceAuthorizationPort authorization) {
        this(canonical, projection, replayPort, authorization, null, null, Clock.systemUTC());
    }

    public HistoricalProjectionReconciliationService(HistoricalProjectionAuditStore canonical,
            HistoricalProjectionInspectionPort projection, HistoricalRelationshipProjectionPort replayPort,
            HistoricalProjectionMaintenanceAuthorizationPort authorization,
            HistoricalProjectionRepairAuditStore repairAudit,
            HistoricalProjectionOperatorContext operatorContext, Clock clock) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.replayPort = Objects.requireNonNull(replayPort, "replayPort");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.repairAudit = repairAudit;
        this.operatorContext = operatorContext;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReconciliationReport reconcile(UUID tenantId, int pageSize, boolean repair) throws Exception {
        authorize(tenantId, repair);
        return withRepairAudit(tenantId, pageSize, repair,
                () -> reconcileAgainst(tenantId, pageSize, repair,
                        projection::find, projection::nextPage, replayPort::project));
    }

    /** Full reconciliation against an isolated staging generation. */
    public ReconciliationReport reconcileGeneration(UUID tenantId, UUID generationId, int pageSize, boolean repair)
            throws Exception {
        Objects.requireNonNull(generationId, "generationId");
        authorize(tenantId, repair);
        if (!(projection instanceof GenerationTargetedHistoricalProjectionInspectionPort inspector)
                || !(replayPort instanceof GenerationTargetedHistoricalRelationshipProjectionPort writer)) {
            throw new IllegalStateException("configured graph adapters do not support generation-targeted reconciliation");
        }
        return withRepairAudit(tenantId, pageSize, repair,
                () -> reconcileAgainst(tenantId, pageSize, repair,
                        (scope, id) -> inspector.findInGeneration(scope, generationId, id),
                        (scope, cursor, limit) -> inspector.nextPageInGeneration(scope, generationId, cursor, limit),
                        event -> writer.projectIntoGeneration(event, generationId)));
    }

    private ReconciliationReport withRepairAudit(UUID tenantId, int pageSize, boolean repair,
            ReconciliationWork work) throws Exception {
        if (!repair) return work.run();
        if (repairAudit == null || operatorContext == null) {
            throw new IllegalStateException("projection repair requires durable audit and authenticated operator context");
        }
        UUID actorId = Objects.requireNonNull(operatorContext.requireActorId(), "operator actor ID is required");
        UUID operationId = repairAudit.start(tenantId, actorId, pageSize, clock.instant());
        try {
            ReconciliationReport report = work.run();
            repairAudit.complete(tenantId, actorId, operationId,
                    new RepairOutcome(pageSize, report.canonicalChecked(), report.missing(), report.stale(),
                            report.divergent(), report.orphaned(), report.repaired()), clock.instant());
            return report;
        } catch (Exception failure) {
            try {
                repairAudit.fail(tenantId, actorId, operationId, pageSize, safeFailure(failure), clock.instant());
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
    }

    private ReconciliationReport reconcileAgainst(UUID tenantId, int pageSize, boolean repair,
            FindProjected find, PageProjected pageProjected, Replay replay) throws Exception {
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be 1..200");
        long checked = 0, missing = 0, stale = 0, divergent = 0, orphaned = 0, repaired = 0;
        String cursor = null;
        while (true) {
            List<CanonicalRelationship> page = canonical.nextLatestPage(tenantId, cursor, pageSize);
            if (page.isEmpty()) break;
            String previous = cursor;
            for (CanonicalRelationship event : page) {
                if (!Objects.equals(tenantId, event.tenantId())) throw new IllegalStateException("canonical audit page crossed tenant scope");
                String id = event.relationshipId().toString();
                if (previous != null && id.compareTo(previous) <= 0) throw new IllegalStateException("canonical audit page is not strictly ordered");
                checked++;
                var graph = find.find(tenantId, event.relationshipId());
                String hash = sha256(event.payload());
                if (graph.isEmpty()) {
                    missing++;
                    if (repair) { replay.project(toProjectionEvent(event)); repaired++; }
                } else {
                    var actual = graph.get();
                    if (actual.revision() < event.revision()) {
                        stale++;
                        if (repair) { replay.project(toProjectionEvent(event)); repaired++; }
                    } else if (actual.revision() > event.revision()) {
                        divergent++; // Never overwrite a graph revision newer than PostgreSQL.
                    } else if (!event.eventKey().equals(actual.eventKey())
                            || !event.relationshipKind().equals(actual.relationshipKind())
                            || !event.subjectKind().equals(actual.subjectKind())
                            || !event.subjectId().equals(actual.subjectId())
                            || !event.objectKind().equals(actual.objectKind())
                            || !event.objectId().equals(actual.objectId())
                            || !hash.equals(actual.payloadHash())) {
                        divergent++;
                        if (repair) { replay.project(toProjectionEvent(event)); repaired++; }
                    }
                }
                previous = id;
            }
            cursor = previous;
            if (page.size() < pageSize) break;
        }

        String graphCursor = null;
        while (true) {
            List<HistoricalProjectionInspectionPort.ProjectedRelationship> page =
                    pageProjected.nextPage(tenantId, graphCursor, pageSize);
            if (page.isEmpty()) break;
            String previous = graphCursor;
            for (var row : page) {
                String id = row.relationshipId().toString();
                if (previous != null && id.compareTo(previous) <= 0) throw new IllegalStateException("graph inspection page is not strictly ordered");
                if (canonical.findLatest(tenantId, row.relationshipId()).isEmpty()) orphaned++;
                previous = id;
            }
            graphCursor = previous;
            if (page.size() < pageSize) break;
        }
        return new ReconciliationReport(tenantId, checked, missing, stale, divergent, orphaned, repaired);
    }

    private void authorize(UUID tenantId, boolean repair) {
        if (tenantId == null) {
            authorization.requireCanAuditPublicCorpus();
            if (repair) authorization.requireCanRebuildPublicCorpus();
        } else {
            authorization.requireCanAuditTenant(tenantId);
            if (repair) authorization.requireCanRebuildTenant(tenantId);
        }
    }

    private static ClaimedProjectionEvent toProjectionEvent(CanonicalRelationship e) {
        UUID token = UUID.nameUUIDFromBytes(e.eventKey().getBytes(StandardCharsets.UTF_8));
        return new ClaimedProjectionEvent(e.eventId(), e.eventKey(), e.eventType(), e.schemaVersion(), e.tenantId(),
                e.relationshipId(), e.revision(), e.payload(), e.occurredAt(), 0, 0, token, java.time.Instant.MAX);
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static String safeFailure(Exception failure) {
        String message = Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()).replaceAll("[\\r\\n\\t]+", " ");
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @FunctionalInterface private interface FindProjected {
        java.util.Optional<HistoricalProjectionInspectionPort.ProjectedRelationship> find(UUID tenantId, UUID id);
    }
    @FunctionalInterface private interface PageProjected {
        List<HistoricalProjectionInspectionPort.ProjectedRelationship> nextPage(UUID tenantId, String cursor, int limit);
    }
    @FunctionalInterface private interface Replay { void project(ClaimedProjectionEvent event) throws Exception; }
    @FunctionalInterface private interface ReconciliationWork { ReconciliationReport run() throws Exception; }

    public record ReconciliationReport(UUID tenantId, long canonicalChecked, long missing, long stale,
            long divergent, long orphaned, long repaired) {
        public boolean clean() { return missing == 0 && stale == 0 && divergent == 0 && orphaned == 0; }
    }
}
