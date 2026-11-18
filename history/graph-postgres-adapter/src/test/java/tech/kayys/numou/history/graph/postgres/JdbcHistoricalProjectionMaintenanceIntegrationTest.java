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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalProjectionMaintenanceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource source;

    @BeforeAll static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(PG.getJdbcUrl()); ds.setUser(PG.getUsername()); ds.setPassword(PG.getPassword());
        source = ds;
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    @Test void rebuildUsesTenantScopeAndImmutableHighWaterMark() throws Exception {
        UUID tenantA = UUID.randomUUID(), tenantB = UUID.randomUUID();
        UUID aBefore = insert(tenantA, "first"), bBefore = insert(tenantB, "other");
        var store = new JdbcHistoricalProjectionRebuildStore(source);
        var run = store.start(tenantA, Instant.now());
        UUID aAfter = insert(tenantA, "later");
        var page = store.nextPage(tenantA, run.runId(), 100);
        assertEquals(List.of(aBefore), page.stream().map(e -> e.aggregateId()).toList());
        assertTrue(page.stream().allMatch(e -> Objects.equals(tenantA, e.tenantId())));
        assertTrue(page.stream().allMatch(e -> e.sequence() <= run.highWaterSequence()));
        assertTrue(page.stream().noneMatch(e -> e.aggregateId().equals(aAfter) || e.aggregateId().equals(bBefore)));
        assertTrue(store.advance(tenantA, run.runId(), 0, page.get(0).sequence(), Instant.now()));
        assertTrue(store.complete(tenantA, run.runId(), page.get(0).sequence(), Instant.now()));
        assertEquals(0, store.nextPage(tenantA, run.runId(), 100).size());
    }

    @Test void publicAndTenantAuditAreIsolatedAndReturnLatestRevision() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID privateId = insert(tenant, "private-v1");
        appendRevision(privateId, tenant, 2, "private-v2");
        UUID publicId = insert(null, "public-v1");
        appendRevision(publicId, null, 2, "public-v2");
        var audit = new JdbcHistoricalProjectionAuditStore(source);
        var privateRows = audit.nextLatestPage(tenant, null, 100);
        var publicRows = audit.nextLatestPage(null, null, 100);
        var privateLatest = privateRows.stream().filter(e -> e.relationshipId().equals(privateId)).findFirst().orElseThrow();
        var publicLatest = publicRows.stream().filter(e -> e.relationshipId().equals(publicId)).findFirst().orElseThrow();
        assertEquals(2, privateLatest.revision());
        assertEquals(2, publicLatest.revision());
        assertEquals("private-v2", description(privateLatest.payload()));
        assertEquals("public-v2", description(publicLatest.payload()));
        assertTrue(privateRows.stream().noneMatch(e -> e.relationshipId().equals(publicId)));
        assertTrue(publicRows.stream().noneMatch(e -> e.relationshipId().equals(privateId)));
        assertEquals(2, audit.findLatest(tenant, privateId).orElseThrow().revision());
    }

    private static UUID insert(UUID tenant, String description) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement("""
            INSERT INTO historical_relationship_revision
              (relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,
               relationship_kind,description,recorded_at,updated_at)
            VALUES (?,1,?,'PERSON',?,'COLLECTIVE',?,'founded',?,?,?)
            """)) {
            s.setObject(1,id); s.setObject(2,tenant); s.setObject(3,UUID.randomUUID()); s.setObject(4,UUID.randomUUID());
            Timestamp now=Timestamp.from(Instant.now()); s.setString(5,description); s.setTimestamp(6,now); s.setTimestamp(7,now);
            s.executeUpdate();
        }
        return id;
    }

    private static void appendRevision(UUID id, UUID tenant, long revision, String description) throws Exception {
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement("""
            INSERT INTO historical_relationship_revision
              (relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,
               relationship_kind,description,recorded_at,updated_at)
            SELECT relationship_id,?,tenant_id,subject_kind,subject_id,object_kind,object_id,
                   relationship_kind,?,recorded_at,updated_at + interval '1 second'
              FROM historical_relationship_revision WHERE relationship_id=? AND revision=?
            """)) {
            s.setLong(1,revision); s.setString(2,description); s.setObject(3,id); s.setLong(4,revision-1);
            assertEquals(1,s.executeUpdate());
        }
    }

    private static String description(String payload) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).path("description").asText();
    }
}