package tech.kayys.numou.history.graph.postgres;
import org.flywaydb.core.Flyway; import org.junit.jupiter.api.BeforeAll; import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource; import org.testcontainers.containers.PostgreSQLContainer; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource; import java.sql.*; import java.time.Instant; import java.util.List; import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalProjectionRebuildStoreIntegrationTest {
 @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine"); static DataSource source;
 @BeforeAll static void migrate(){PGSimpleDataSource ds=new PGSimpleDataSource();ds.setURL(PG.getJdbcUrl());ds.setUser(PG.getUsername());ds.setPassword(PG.getPassword());source=ds;Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 @Test void replayPagesAreTenantScopedAndStopAtCapturedHighWaterMark() throws Exception {
  UUID tenantA=UUID.randomUUID(),tenantB=UUID.randomUUID(); UUID publicId=insertRevision(null),tenantAId=insertRevision(tenantA),tenantBId=insertRevision(tenantB);
  var store=new JdbcHistoricalProjectionRebuildStore(source);var publicRun=store.start(null,Instant.now());var tenantRun=store.start(tenantA,Instant.now());UUID afterSnapshot=insertRevision(tenantA);
  List<HistoricalProjectionRebuildStore.ReplayEvent> publicPage=store.nextPage(null,publicRun.runId(),20);List<HistoricalProjectionRebuildStore.ReplayEvent> tenantPage=store.nextPage(tenantA,tenantRun.runId(),20);
  assertEquals(List.of(publicId),publicPage.stream().map(HistoricalProjectionRebuildStore.ReplayEvent::aggregateId).toList());
  assertEquals(List.of(tenantAId),tenantPage.stream().map(HistoricalProjectionRebuildStore.ReplayEvent::aggregateId).toList());
  assertFalse(tenantPage.stream().anyMatch(e->e.aggregateId().equals(tenantBId)||e.aggregateId().equals(afterSnapshot)));
  assertTrue(tenantPage.get(0).sequence()<=tenantRun.highWaterSequence());
 }
 @Test void checkpointIsScopeBoundAndCompletionWaitsUntilSnapshotIsConsumed() throws Exception {
  UUID tenant=UUID.randomUUID(),aggregate=insertRevision(tenant);var store=new JdbcHistoricalProjectionRebuildStore(source);var run=store.start(tenant,Instant.now());
  assertTrue(store.nextPage(tenant,run.runId(),10).stream().anyMatch(e->e.aggregateId().equals(aggregate)));
  assertFalse(store.advance(UUID.randomUUID(),run.runId(),0,1,Instant.now()));
  var page=store.nextPage(tenant,run.runId(),10);assertEquals(1,page.size());
  assertTrue(store.advance(tenant,run.runId(),0,page.get(0).sequence(),Instant.now()));
  assertTrue(store.complete(tenant,run.runId(),page.get(0).sequence(),Instant.now()));
  assertEquals(HistoricalProjectionRebuildStore.RebuildRun.Status.COMPLETED,store.find(tenant,run.runId()).orElseThrow().status());
  assertTrue(store.nextPage(tenant,run.runId(),10).isEmpty());
 }
 @Test void callerAssignedRunIdMakesCoordinatorRetriesIdempotent() {
  UUID tenant=UUID.randomUUID(),runId=UUID.randomUUID();var store=new JdbcHistoricalProjectionRebuildStore(source);
  var first=store.start(tenant,runId,Instant.now());var retry=store.start(tenant,runId,Instant.now().plusSeconds(10));
  assertEquals(runId,first.runId());assertEquals(first,retry);
 }
 private static UUID insertRevision(UUID tenant) throws Exception {
  UUID id=UUID.randomUUID();try(Connection c=source.getConnection()){c.setAutoCommit(false);
   if(tenant!=null)try(PreparedStatement scope=c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")){scope.setString(1,tenant.toString());scope.execute();}
   try(PreparedStatement s=c.prepareStatement("INSERT INTO historical_relationship_revision(relationship_id,revision,tenant_id,subject_kind,subject_id,object_kind,object_id,relationship_kind,description,recorded_at,updated_at) VALUES (?,1,?,'PERSON',?,'COLLECTIVE',?,'founded','integration',?,?)")){
    s.setObject(1,id);s.setObject(2,tenant);s.setObject(3,UUID.randomUUID());s.setObject(4,UUID.randomUUID());Timestamp now=Timestamp.from(Instant.now());s.setTimestamp(5,now);s.setTimestamp(6,now);s.executeUpdate();}
   c.commit();}return id;
 }
}