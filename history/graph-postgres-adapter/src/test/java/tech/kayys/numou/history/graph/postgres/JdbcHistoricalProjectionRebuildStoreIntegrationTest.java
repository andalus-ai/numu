package tech.kayys.numou.history.graph.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalProjectionRebuildStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource source;

    @BeforeAll static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(PG.getJdbcUrl()); ds.setUser(PG.getUsername()); ds.setPassword(PG.getPassword());
        source = ds;
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    @Test void replayPagesAreTenantScopedAndStopAtCapturedHighWaterMark() throws Exception {
        UUID tenantA = UUID.randomUUID(), tenantB = UUID.randomUUID();
        UUID publicId = insertRevision(null), tenantAId = insertRevision(tenantA), tenantBId = insertRevision(tenantB);
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var publicRun = store.start(null, Instant.now());
        var tenantRun = store.start(tenantA, Instant.now());
        UUID afterSnapshot = insertRevision(tenantA);

        List<HistoricalProjectionRebuildStore.ReplayEvent> publicPage = store.nextPage(null, publicRun.runId(), 20);
        List<HistoricalProjectionRebuildStore.ReplayEvent> tenantPage = store.nextPage(tenantA, tenantRun.runId(), 20);
        assertEquals(List.of(publicId), publicPage.stream().map(HistoricalProjectionRebuildStore.ReplayEvent::aggregateId).toList());
        assertEquals(List.of(tenantAId), tenantPage.stream().map(HistoricalProjectionRebuildStore.ReplayEvent::aggregateId).toList());
        assertFalse(tenantPage.stream().anyMatch(e -> e.aggregateId().equals(tenantBId) || e.aggregateId().equals(afterSnapshot)));
        assertTrue(tenantPage.get(0).sequence() <= tenantRun.highWaterSequence());
    }

    @Test void checkpointAndCompletionAreVersionFencedAndScopeBound() throws Exception {
        UUID tenant = UUID.randomUUID();
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var run = store.start(tenant, Instant.now());
        assertTrue(store.nextPage(tenant, run.runId(), 10).isEmpty());
        UUID actualTenant = UUID.randomUUID(), actualAggregate = insertRevision(actualTenant);
        var actualRun = store.start(actualTenant, Instant.now());
        var page = store.nextPage(actualTenant, actualRun.runId(), 10);
        assertEquals(1, page.size());
        assertTrue(page.stream().anyMatch(e -> e.aggregateId().equals(actualAggregate)));
        assertTrue(store.find(tenant, actualRun.runId()).isEmpty());
        assertFalse(store.advanceFenced(tenant, actualRun.runId(), 0, page.get(0).sequence(), 0, Instant.now()));
        assertTrue(store.advanceFenced(actualTenant, actualRun.runId(), 0, page.get(0).sequence(), 0, Instant.now()));
        assertFalse(store.advanceFenced(actualTenant, actualRun.runId(), 0, page.get(0).sequence(), 0, Instant.now()));
        assertTrue(store.completeFenced(actualTenant, actualRun.runId(), page.get(0).sequence(), 1, Instant.now()));
        assertEquals(HistoricalProjectionRebuildStore.RebuildRun.Status.COMPLETED,
                store.find(actualTenant, actualRun.runId()).orElseThrow().status());
        assertTrue(store.nextPage(actualTenant, actualRun.runId(), 10).isEmpty());
    }

    @Test void lifecycleTransitionsAreAtomicAuditedAndFenceStaleWorkers() throws Exception {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        insertRevision(tenant);
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var run = store.start(tenant, Instant.now());

        assertTrue(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.PAUSE,
                0, HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.PAUSED, "planned maintenance", Instant.now()));
        assertFalse(store.advanceFenced(tenant, run.runId(), 0, 1, 0, Instant.now()));
        var paused = store.find(tenant, run.runId()).orElseThrow();
        assertEquals(HistoricalProjectionRebuildStore.RebuildRun.Status.PAUSED, paused.status());
        assertEquals(1, paused.lifecycleVersion());

        assertTrue(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.RESUME,
                1, HistoricalProjectionRebuildStore.RebuildRun.Status.PAUSED,
                HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING, "maintenance complete", Instant.now()));
        assertFalse(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.PAUSE,
                1, HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.PAUSED, "stale operator", Instant.now()));
        assertTrue(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.CANCEL,
                2, HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.CANCELLED, "operator cancelled", Instant.now()));

        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            setScope(c, tenant);
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT COUNT(*), COUNT(DISTINCT actor_id) FROM historical_projection_rebuild_audit WHERE run_id=?")) {
                s.setObject(1, run.runId());
                try (ResultSet rs = s.executeQuery()) {
                    assertTrue(rs.next()); assertEquals(3, rs.getLong(1)); assertEquals(1, rs.getLong(2));
                }
            }
            c.commit();
        }
        assertEquals(HistoricalProjectionRebuildStore.RebuildRun.Status.CANCELLED,
                store.find(tenant, run.runId()).orElseThrow().status());
    }

    @Test void failedRunCanBeRetriedWithoutLosingCursor() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var run = store.start(tenant, Instant.now());
        assertTrue(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.FAIL,
                0, HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.FAILED, "provider unavailable", Instant.now()));
        var failed = store.find(tenant, run.runId()).orElseThrow();
        assertEquals("provider unavailable", failed.failureDetail());
        assertEquals(0, failed.cursorSequence());
        assertTrue(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.RETRY,
                1, HistoricalProjectionRebuildStore.RebuildRun.Status.FAILED,
                HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING, "provider recovered", Instant.now()));
        var retried = store.find(tenant, run.runId()).orElseThrow();
        assertEquals(HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING, retried.status());
        assertEquals("", retried.failureDetail());
        assertEquals(2, retried.lifecycleVersion());
    }

    @Test void invalidTransitionsAndStaleLifecycleVersionsAreRejected() {
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var run = store.start(tenant, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> store.transition(tenant, run.runId(), actor,
                HistoricalProjectionRebuildStore.Action.RESUME, 0,
                HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.COMPLETED, "invalid", Instant.now()));
        assertFalse(store.transition(tenant, run.runId(), actor, HistoricalProjectionRebuildStore.Action.PAUSE,
                99, HistoricalProjectionRebuildStore.RebuildRun.Status.RUNNING,
                HistoricalProjectionRebuildStore.RebuildRun.Status.PAUSED, "stale", Instant.now()));
        assertEquals(0, store.find(tenant, run.runId()).orElseThrow().lifecycleVersion());
    }

    private static void setScope(Connection c, UUID tenant) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
            s.setString(1, tenant.toString()); s.execute();
        }
    }

    private static UUID insertRevision(UUID tenant) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            if (tenant != null) setScope(c, tenant);
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO historical_relationship_revision
                    (relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,
                     relationship_kind,description,recorded_at,updated_at)
                    VALUES (?,1,?,'PERSON',?,'COLLECTIVE',?,'founded','integration',?,?)
                    """)) {
                s.setObject(1, id); s.setObject(2, tenant); s.setObject(3, UUID.randomUUID());
                s.setObject(4, UUID.randomUUID());
                Timestamp now = Timestamp.from(Instant.now()); s.setTimestamp(5, now); s.setTimestamp(6, now);
                s.executeUpdate();
            }
            c.commit();
        }
        return id;
    }
}
