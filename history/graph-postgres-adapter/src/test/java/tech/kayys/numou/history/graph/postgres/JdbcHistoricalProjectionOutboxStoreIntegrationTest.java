package tech.kayys.numou.history.graph.postgres;
import org.flywaydb.core.Flyway; import org.junit.jupiter.api.BeforeAll; import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource; import org.testcontainers.containers.PostgreSQLContainer; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource; import java.sql.*; import java.time.Instant; import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers(disabledWithoutDocker=true)
class JdbcHistoricalProjectionOutboxStoreIntegrationTest {
 @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
 static DataSource source;
 @BeforeAll static void migrate(){PGSimpleDataSource ds=new PGSimpleDataSource();ds.setURL(PG.getJdbcUrl());ds.setUser(PG.getUsername());ds.setPassword(PG.getPassword());source=ds;Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 @Test void leaseTokenFencesOldWorkerAndExpiredLeaseCanBeReclaimed() throws Exception {
  UUID aggregate=insertEvent();var store=new JdbcHistoricalProjectionOutboxStore(source);Instant now=Instant.now();
  var first=store.claimDue("worker-a",now,now.plusSeconds(1),20).stream().filter(e->e.aggregateId().equals(aggregate)).findFirst().orElseThrow();
  assertFalse(store.markPublished(first.eventId(),UUID.randomUUID(),now));
  var second=store.claimDue("worker-b",now.plusSeconds(2),now.plusSeconds(30),20).stream().filter(e->e.eventId().equals(first.eventId())).findFirst().orElseThrow();
  assertNotEquals(first.leaseToken(),second.leaseToken());assertFalse(store.markPublished(first.eventId(),first.leaseToken(),now.plusSeconds(2)));
  assertTrue(store.markPublished(second.eventId(),second.leaseToken(),now.plusSeconds(3)));
 }
 @Test void deadLetterReplayIsAuditedAndResetsOnlyRetryWindow() throws Exception {
  UUID aggregate=insertEvent();var store=new JdbcHistoricalProjectionOutboxStore(source);Instant now=Instant.now();
  var e=store.claimDue("worker",now,now.plusSeconds(30),50).stream().filter(x->x.aggregateId().equals(aggregate)).findFirst().orElseThrow();
  assertTrue(store.markDead(e.eventId(),e.leaseToken(),"permanent",now));assertEquals(1,store.listDead(100).stream().filter(x->x.eventId().equals(e.eventId())).count());
  assertTrue(store.requeueDead(e.eventId(),UUID.randomUUID(),now.plusSeconds(1)));
  try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement("SELECT attempts,retry_attempts,dead_lettered_at FROM historical_relationship_projection_outbox WHERE event_id=?")){s.setObject(1,e.eventId());try(ResultSet r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));assertEquals(0,r.getInt(2));assertNull(r.getTimestamp(3));}}
  try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement("SELECT count(*) FROM historical_relationship_projection_replay_audit WHERE event_id=?")){s.setObject(1,e.eventId());try(ResultSet r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));}}
  assertThrows(SQLException.class,()->{try(Connection c=source.getConnection();Statement s=c.createStatement()){s.executeUpdate("UPDATE historical_relationship_projection_replay_audit SET actor_id=gen_random_uuid()");}});
 }
 private static UUID insertEvent() throws Exception {
  UUID id=UUID.randomUUID();try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement("""
   INSERT INTO historical_relationship_revision(relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,relationship_kind,description,recorded_at,updated_at)
   VALUES (?,1,NULL,'PERSON',?,'COLLECTIVE',?,'founded','integration',?,?)
   """)){s.setObject(1,id);s.setObject(2,UUID.randomUUID());s.setObject(3,UUID.randomUUID());Timestamp now=Timestamp.from(Instant.now());s.setTimestamp(4,now);s.setTimestamp(5,now);s.executeUpdate();}return id;
 }
}