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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalProjectionOperationalStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource source;

    @BeforeAll static void migrate() {
        PGSimpleDataSource ds=new PGSimpleDataSource();
        ds.setURL(PG.getJdbcUrl()); ds.setUser(PG.getUsername()); ds.setPassword(PG.getPassword());
        source=ds;
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    @Test void reportsOutboxBacklogAndRebuildProgressWithinTenantScope() throws Exception {
        UUID tenant=UUID.randomUUID();
        UUID aggregate=insertRevision(tenant);
        var rebuilds=new JdbcHistoricalProjectionRebuildStore(source);
        var run=rebuilds.start(tenant,Instant.now());
        var store=new JdbcHistoricalProjectionOperationalStore(source);

        var snapshot=store.snapshot(tenant,Instant.now());

        assertEquals(tenant,snapshot.tenantId());
        assertTrue(snapshot.pendingEvents()>=1);
        assertEquals(0,snapshot.leasedEvents());
        assertEquals(0,snapshot.deadLetterEvents());
        assertTrue(snapshot.oldestPendingAgeSeconds()>=0);
        assertEquals(1,snapshot.runningRebuilds());
        assertTrue(snapshot.rebuildEventsRemaining()>=1);
        assertTrue(snapshot.oldestRebuildAgeSeconds()>=0);
        assertTrue(rebuilds.find(tenant,run.runId()).isPresent());
        assertNotNull(aggregate);
    }

    @Test void publicSnapshotDoesNotRequireTenantContext() throws Exception {
        insertRevision(null);
        var snapshot=new JdbcHistoricalProjectionOperationalStore(source).snapshot(null,Instant.now());
        assertNull(snapshot.tenantId());
        assertTrue(snapshot.pendingEvents()>=1);
    }

    private static UUID insertRevision(UUID tenant) throws Exception {
        UUID id=UUID.randomUUID();
        try(Connection c=source.getConnection()) {
            c.setAutoCommit(false);
            if(tenant!=null) try(PreparedStatement scope=c.prepareStatement(
                    "SELECT set_config('numou.tenant_id', ?, true)")) {
                scope.setString(1,tenant.toString()); scope.execute();
            }
            try(PreparedStatement s=c.prepareStatement("""
                INSERT INTO historical_relationship_revision
                  (relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,
                   relationship_kind,description,recorded_at,updated_at)
                VALUES (?,1,?,'PERSON',?,'COLLECTIVE',?,'founded','metrics fixture',?,?)
                """)) {
                s.setObject(1,id); s.setObject(2,tenant); s.setObject(3,UUID.randomUUID());
                s.setObject(4,UUID.randomUUID()); Timestamp now=Timestamp.from(Instant.now());
                s.setTimestamp(5,now); s.setTimestamp(6,now); s.executeUpdate();
            }
            c.commit();
        }
        return id;
    }
}
