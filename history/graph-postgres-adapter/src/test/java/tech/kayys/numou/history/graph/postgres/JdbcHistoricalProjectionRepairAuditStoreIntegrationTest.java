package tech.kayys.numou.history.graph.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class JdbcHistoricalProjectionRepairAuditStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcHistoricalProjectionRepairAuditStore store;
    static DataSourceHolder holder;

    @BeforeAll static void migrate() {
        PGSimpleDataSource ds=new PGSimpleDataSource();
        ds.setURL(PG.getJdbcUrl()); ds.setUser(PG.getUsername()); ds.setPassword(PG.getPassword());
        holder=new DataSourceHolder(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        store=new JdbcHistoricalProjectionRepairAuditStore(ds);
    }

    @Test void appendsStartedAndCompletedRowsWithActorAndOutcome() throws Exception {
        UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();
        Instant now=Instant.now();
        UUID operation=store.start(tenant,actor,25,now);
        store.complete(tenant,actor,operation,
                new tech.kayys.numou.history.graph.spi.HistoricalProjectionRepairAuditStore.RepairOutcome(
                        25,100,2,3,4,1,5),now.plusSeconds(1));
        try(Connection c=holder.source.getConnection();PreparedStatement s=c.prepareStatement(
                "SELECT phase,page_size,canonical_checked,repaired,actor_id FROM historical_projection_repair_audit WHERE operation_id=? ORDER BY occurred_at,audit_id")) {
            s.setObject(1,operation);
            try(ResultSet r=s.executeQuery()) {
                assertTrue(r.next()); assertEquals("STARTED",r.getString(1)); assertEquals(25,r.getInt(2));
                assertEquals(0,r.getLong(3)); assertEquals(0,r.getLong(4)); assertEquals(actor,r.getObject(5,UUID.class));
                assertTrue(r.next()); assertEquals("COMPLETED",r.getString(1)); assertEquals(25,r.getInt(2));
                assertEquals(100,r.getLong(3)); assertEquals(5,r.getLong(4)); assertEquals(actor,r.getObject(5,UUID.class));
                assertFalse(r.next());
            }
        }
    }

    @Test void auditRowsCannotBeUpdatedOrDeleted() throws Exception {
        UUID actor=UUID.randomUUID(),operation=store.start(null,actor,10,Instant.now());
        assertThrows(SQLException.class,()->{
            try(Connection c=holder.source.getConnection();PreparedStatement s=c.prepareStatement(
                    "UPDATE historical_projection_repair_audit SET detail='tampered' WHERE operation_id=?")) {
                s.setObject(1,operation); s.executeUpdate();
            }
        });
    }

    private record DataSourceHolder(PGSimpleDataSource source) {}
}
