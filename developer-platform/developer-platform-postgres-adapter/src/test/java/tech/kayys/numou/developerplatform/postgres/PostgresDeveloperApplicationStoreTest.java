package tech.kayys.numou.developerplatform.postgres;

import static org.assertj.core.api.Assertions.*;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.developerplatform.application.DeveloperApplicationStore;
import tech.kayys.numou.developerplatform.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

@Testcontainers(disabledWithoutDocker = true)
class PostgresDeveloperApplicationStoreTest {
 @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
  .withDatabaseName("developer_platform").withUsername("migration_owner").withPassword("migration_owner");
 static PGSimpleDataSource owner;
 static DataSource runtime;
 PostgresDeveloperApplicationStore store;
 static final Instant NOW=Instant.parse("2026-11-04T00:00:00Z");

 @BeforeAll static void migrateAndRestrictRuntimeRole() throws Exception {
  owner=new PGSimpleDataSource(); owner.setURL(POSTGRES.getJdbcUrl()); owner.setUser(POSTGRES.getUsername()); owner.setPassword(POSTGRES.getPassword());
  Flyway.configure().dataSource(owner).locations("classpath:db/migration").load().migrate();
  try(Connection c=owner.getConnection();var s=c.createStatement()){
   s.execute("CREATE ROLE developer_registry_runtime NOLOGIN NOBYPASSRLS");
   s.execute("GRANT USAGE ON SCHEMA public TO developer_registry_runtime");
   s.execute("GRANT SELECT, INSERT, UPDATE ON developer_applications, developer_application_idempotency, developer_application_events, developer_application_outbox TO developer_registry_runtime");
  }
  runtime=new RoleDataSource(owner);
 }
 @BeforeEach void freshStore(){store=new PostgresDeveloperApplicationStore(runtime);}

 @Test void tenantIsolationAndTenantLocalSlugUniqueness(){
  TenantId tenantA=TenantId.generate(),tenantB=TenantId.generate();
  var a=register(tenantA,"shared-app","key-tenant-a-0001");
  var b=register(tenantB,"shared-app","key-tenant-b-0001");
  assertThat(a.outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.CREATED);
  assertThat(b.outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.CREATED);
  assertThat(store.find(tenantA,b.application().orElseThrow().id()).toCompletableFuture().join()).isEmpty();
  assertThat(store.list(tenantA,null,100).toCompletableFuture().join()).extracting(DeveloperApplication::slug).containsExactly("shared-app");
  assertThat(register(tenantA,"shared-app","key-tenant-a-0002").outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.CONFLICT);
 }
 @Test void concurrentSlugClaimsCannotCreateDuplicates(){
  TenantId tenant=TenantId.generate();
  var first=DeveloperApplication.register(tenant,"race-app","Race A","","actor-1",NOW);
  var second=DeveloperApplication.register(tenant,"race-app","Race B","","actor-2",NOW);
  var f1=java.util.concurrent.CompletableFuture.supplyAsync(()->store.registerIfAbsent(first,sha("race-idempotency-key-0001"),sha("race-a"),event(first,"DEVELOPER_APPLICATION_REGISTERED",1)).toCompletableFuture().join());
  var f2=java.util.concurrent.CompletableFuture.supplyAsync(()->store.registerIfAbsent(second,sha("race-idempotency-key-0002"),sha("race-b"),event(second,"DEVELOPER_APPLICATION_REGISTERED",1)).toCompletableFuture().join());
  var outcomes=java.util.List.of(f1.join().outcome(),f2.join().outcome());
  assertThat(outcomes).containsExactlyInAnyOrder(DeveloperApplicationStore.CreateResult.Outcome.CREATED,DeveloperApplicationStore.CreateResult.Outcome.CONFLICT);
  assertThat(store.list(tenant,null,100).toCompletableFuture().join()).hasSize(1);
 }
 @Test void registrationIsIdempotentAndRejectsKeyReuseWithDifferentPayload(){
  TenantId tenant=TenantId.generate();
  var first=register(tenant,"idempotent-app","stable-idempotency-key-0001");
  var replay=register(tenant,"idempotent-app","stable-idempotency-key-0001");
  assertThat(first.outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.CREATED);
  assertThat(replay.outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.REPLAYED);
  var changed=DeveloperApplication.register(tenant,"idempotent-app","Different name","","actor-1",NOW);
  var changedEvent=event(changed,"DEVELOPER_APPLICATION_REGISTERED",1);
  var conflict=store.registerIfAbsent(changed,sha("stable-idempotency-key-0001"),sha("different-fingerprint"),changedEvent).toCompletableFuture().join();
  assertThat(conflict.outcome()).isEqualTo(DeveloperApplicationStore.CreateResult.Outcome.CONFLICT);
 }
 @Test void optimisticUpdateWritesEventAndOutboxAtomically(){
  TenantId tenant=TenantId.generate();var app=register(tenant,"revision-app","revision-idempotency-key-01").application().orElseThrow();
  var event=app.updateProfile("Updated name","updated description","actor-2","corr-2",NOW.plusSeconds(5));
  assertThat(store.update(app,1,event).toCompletableFuture().join()).isTrue();
  assertThat(store.update(app,1,event).toCompletableFuture().join()).isFalse();
  assertThat(store.find(tenant,app.id()).toCompletableFuture().join().orElseThrow().revision()).isEqualTo(2);
  assertThat(count("developer_application_events",tenant)).isEqualTo(2);
  assertThat(count("developer_application_outbox",tenant)).isEqualTo(2);
 }
 @Test void failedOutboxInsertRollsBackApplicationAndEventMutation() throws Exception {
  TenantId tenant=TenantId.generate();var app=register(tenant,"rollback-app","rollback-idempotency-key-01").application().orElseThrow();
  try(Connection c=owner.getConnection();var s=c.createStatement()){
   s.execute("CREATE FUNCTION fail_developer_outbox_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected outbox failure'; END $$");
   s.execute("CREATE TRIGGER fail_developer_outbox_test BEFORE INSERT ON developer_application_outbox FOR EACH ROW EXECUTE FUNCTION fail_developer_outbox_test()");
  }
  var event=app.updateProfile("Should roll back","","actor-2","corr-3",NOW.plusSeconds(5));
  assertThatThrownBy(()->store.update(app,1,event).toCompletableFuture().join()).isInstanceOf(CompletionException.class);
  try(Connection c=owner.getConnection();var s=c.prepareStatement("SELECT revision,name FROM developer_applications WHERE tenant_id=? AND id=?")){
   s.setObject(1,tenant.value());s.setObject(2,app.id().value());try(var rs=s.executeQuery()){assertThat(rs.next()).isTrue();assertThat(rs.getLong(1)).isEqualTo(1);assertThat(rs.getString(2)).isEqualTo("rollback-app");}
  }
  assertThat(count("developer_application_events",tenant)).isEqualTo(1);
  assertThat(count("developer_application_outbox",tenant)).isEqualTo(1);
 }
 private DeveloperApplicationStore.CreateResult register(TenantId tenant,String slug,String key){
  var app=DeveloperApplication.register(tenant,slug,slug,"","actor-1",NOW);
  return store.registerIfAbsent(app,sha(key),sha(app.slug()+"\u0000"+app.name()+"\u0000"+app.description()),event(app,"DEVELOPER_APPLICATION_REGISTERED",1)).toCompletableFuture().join();
 }
 private static DeveloperApplicationEvent event(DeveloperApplication app,String type,long revision){return new DeveloperApplicationEvent(UUID.randomUUID(),app.tenantId(),app.id(),type,revision,app.createdBy(),"",app.createdAt());}
 private static String sha(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private long count(String table,TenantId tenant){try(Connection c=owner.getConnection();var s=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE tenant_id=?")){s.setObject(1,tenant.value());try(var rs=s.executeQuery()){rs.next();return rs.getLong(1);}}catch(SQLException e){throw new IllegalStateException(e);}}

 /** Application role is deliberately not a superuser and has no BYPASSRLS privilege. */
 private static final class RoleDataSource implements DataSource{
  private final DataSource delegate;private RoleDataSource(DataSource delegate){this.delegate=delegate;}
  @Override public Connection getConnection()throws SQLException{Connection c=delegate.getConnection();try(var s=c.createStatement()){s.execute("SET ROLE developer_registry_runtime");return c;}catch(SQLException e){c.close();throw e;}}
  @Override public Connection getConnection(String u,String p)throws SQLException{return getConnection();}
  @Override public PrintWriter getLogWriter()throws SQLException{return delegate.getLogWriter();}
  @Override public void setLogWriter(PrintWriter out)throws SQLException{delegate.setLogWriter(out);}
  @Override public void setLoginTimeout(int seconds)throws SQLException{delegate.setLoginTimeout(seconds);}
  @Override public int getLoginTimeout()throws SQLException{return delegate.getLoginTimeout();}
  @Override public Logger getParentLogger(){return Logger.getLogger("developer-platform-test");}
  @Override public <T>T unwrap(Class<T> iface)throws SQLException{return delegate.unwrap(iface);}
  @Override public boolean isWrapperFor(Class<?> iface)throws SQLException{return delegate.isWrapperFor(iface);}
 }
}