package tech.kayys.numou.developer.apicatalog.postgres;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.ActorId;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.developer.apicatalog.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

@Testcontainers(disabledWithoutDocker=true)
class PostgresApiProductRepositoryTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
  .withDatabaseName("api_catalog").withUsername("migration_owner").withPassword("migration_owner");
 static PGSimpleDataSource owner;static DataSource runtime;
 PostgresApiProductRepository repository;ApiCatalogJdbcTransactionManager transactions;
 static final Instant NOW=Instant.parse("2026-11-04T10:00:00Z");

 @BeforeAll static void migrate()throws Exception{
  owner=new PGSimpleDataSource();owner.setURL(POSTGRES.getJdbcUrl());owner.setUser(POSTGRES.getUsername());owner.setPassword(POSTGRES.getPassword());
  Flyway.configure().dataSource(owner).locations("classpath:db/migration/api-catalog").load().migrate();
  try(Connection c=owner.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE ROLE api_catalog_runtime NOLOGIN NOBYPASSRLS");
   s.execute("CREATE TABLE platform_outbox (entry_id uuid PRIMARY KEY,event_id uuid NOT NULL UNIQUE,tenant_id uuid,event_type text NOT NULL,payload jsonb NOT NULL,created_at timestamptz NOT NULL,available_at timestamptz NOT NULL,attempts integer NOT NULL DEFAULT 0,locked_until timestamptz,published_at timestamptz,last_error text)");
   s.execute("GRANT USAGE ON SCHEMA public TO api_catalog_runtime");
   s.execute("GRANT SELECT,INSERT,UPDATE ON developer_api_products,developer_api_versions,developer_api_scopes,developer_api_artifacts TO api_catalog_runtime");
   s.execute("GRANT SELECT,INSERT ON platform_outbox TO api_catalog_runtime");
  }
  runtime=new RoleDataSource(owner);
 }
 @BeforeEach void setup(){transactions=new ApiCatalogJdbcTransactionManager(runtime);repository=new PostgresApiProductRepository(transactions);}

 @Test void roundTripsProductVersionsAndScopesWithoutEmittingCreationEventOnLoad(){
  TenantId tenant=TenantId.generate();ApiProduct product=product(tenant,"orders");
  repository.save(tenant,product,0).await().indefinitely();
  var loaded=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(loaded.revision()).isEqualTo(1);
  assertThat(loaded.pullDomainEvents()).isEmpty();
  loaded.addVersion(version("1.0.0"),NOW.plusSeconds(1));
  repository.save(tenant,loaded,1).await().indefinitely();
  assertThat(artifactState(tenant,product.id().value(),"1.0.0")).isEqualTo("REGISTERED");
  var withVersion=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(withVersion.versions()).containsKey("1.0.0");
  assertThat(withVersion.versions().get("1.0.0").scopes()).containsKey("orders.read");
  withVersion.publishVersion("1.0.0",NOW.plusSeconds(2));
  repository.save(tenant,withVersion,2).await().indefinitely();
  var published=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(published.status()).isEqualTo(ApiProductStatus.ACTIVE);
  assertThat(published.versions().get("1.0.0").status()).isEqualTo(ApiVersionStatus.PUBLISHED);
  assertThat(artifactState(tenant,product.id().value(),"1.0.0")).isEqualTo("PUBLISHED");
  assertPublishedArtifactCannotBeScheduledForDeletion(tenant,product.id().value(),"1.0.0");
 }

 @Test void enforcesTenantIsolationAndTenantScopedSlugUniqueness(){
  TenantId tenantA=TenantId.generate(),tenantB=TenantId.generate();
  ApiProduct a=product(tenantA,"shared");ApiProduct b=product(tenantB,"shared");
  repository.save(tenantA,a,0).await().indefinitely();repository.save(tenantB,b,0).await().indefinitely();
  var bySlug=repository.findBySlug(tenantA,"shared").await().indefinitely().orElseThrow();
  assertThat(bySlug.id()).isEqualTo(a.id());
  assertThat(repository.findById(tenantA,b.id()).await().indefinitely()).isEmpty();
  assertThat(repository.findAll(tenantA,tech.kayys.numou.foundation.application.page.PageRequest.first(10)).await().indefinitely().content())
    .extracting(ApiProduct::slug).containsExactly("shared");
  ApiProduct duplicate=product(tenantA,"shared");
  assertThatThrownBy(()->repository.save(tenantA,duplicate,0).await().indefinitely()).isInstanceOf(RuntimeException.class);
 }

 @Test void rejectsStaleRevisionWithoutOverwritingAggregate(){
  TenantId tenant=TenantId.generate();ApiProduct product=product(tenant,"stale");
  repository.save(tenant,product,0).await().indefinitely();
  var first=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  var stale=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  first.addVersion(version("1.0.0"),NOW.plusSeconds(1));repository.save(tenant,first,1).await().indefinitely();
  stale.addVersion(version("2.0.0"),NOW.plusSeconds(2));
  assertThatThrownBy(()->repository.save(tenant,stale,1).await().indefinitely())
    .isInstanceOf(PostgresApiProductRepository.OptimisticLockException.class);
  assertThat(repository.findById(tenant,product.id()).await().indefinitely().orElseThrow().versions()).containsKey("1.0.0");
 }


 @Test void claimsOnlyExpiredAbandonedArtifactsAndTombstonesAfterIdempotentDeletion(){
  TenantId tenant=TenantId.generate();ApiProduct product=product(tenant,"cleanup-ready");
  repository.save(tenant,product,0).await().indefinitely();
  var aggregate=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  aggregate.addVersion(version("1.0.0"),NOW.plusSeconds(1));
  repository.save(tenant,aggregate,1).await().indefinitely();
  markArtifactAbandoned(tenant,product.id().value(),"1.0.0",NOW.minusSeconds(60));

  var cleanup=new PostgresApiArtifactCleanupRepository(transactions);
  var leases=cleanup.claimDue(tenant,"cleanup-worker:unique-token",NOW,NOW.plusSeconds(90),10,5).await().indefinitely();
  assertThat(leases).hasSize(1);
  assertThat(leases.get(0).artifactReference()).isEqualTo("rustfs://api-specs/orders.yaml");
  assertThat(cleanup.isLeaseCurrent(tenant,leases.get(0),"cleanup-worker:unique-token",NOW,5).await().indefinitely()).isTrue();

  cleanup.markDeleted(tenant,leases.get(0),"cleanup-worker:unique-token",NOW.plusSeconds(1)).await().indefinitely();
  assertThat(artifactState(tenant,product.id().value(),"1.0.0")).isEqualTo("DELETED");
  assertThat(cleanup.claimDue(tenant,"another-worker",NOW.plusSeconds(2),NOW.plusSeconds(92),10,5)
    .await().indefinitely()).isEmpty();
 }

 @Test void cleanupFailureUsesBoundedAttemptsAndReleasesTheLease(){
  TenantId tenant=TenantId.generate();ApiProduct product=product(tenant,"cleanup-retry");
  repository.save(tenant,product,0).await().indefinitely();
  var aggregate=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  aggregate.addVersion(version("1.0.0"),NOW.plusSeconds(1));
  repository.save(tenant,aggregate,1).await().indefinitely();
  markArtifactAbandoned(tenant,product.id().value(),"1.0.0",NOW.minusSeconds(60));

  var cleanup=new PostgresApiArtifactCleanupRepository(transactions);
  var lease=cleanup.claimDue(tenant,"cleanup-worker:retry-token",NOW,NOW.plusSeconds(90),1,1)
    .await().indefinitely().get(0);
  cleanup.recordFailure(tenant,lease,"cleanup-worker:retry-token","StorageUnavailable",
    NOW.plusSeconds(5),1).await().indefinitely();

  assertThat(artifactState(tenant,product.id().value(),"1.0.0")).isEqualTo("ABANDONED");
  try(Connection connection=runtime.getConnection()){
   try(PreparedStatement context=connection.prepareStatement("SELECT set_config('numou.tenant_id',?,false)")){
    context.setString(1,tenant.value().toString());context.execute();
   }
   try(PreparedStatement query=connection.prepareStatement("""
    SELECT cleanup_attempts,cleanup_last_error_code,cleanup_lease_owner
    FROM developer_api_artifacts WHERE provider_tenant_id=? AND product_id=? AND api_version=?
    """)){
    query.setObject(1,tenant.value());query.setObject(2,product.id().value());query.setString(3,"1.0.0");
    try(ResultSet rows=query.executeQuery()){
     assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);
     assertThat(rows.getString(2)).isEqualTo("StorageUnavailable");assertThat(rows.getString(3)).isNull();
    }
   }
  }catch(SQLException failure){throw new IllegalStateException(failure);}
  assertThat(cleanup.claimDue(tenant,"cleanup-worker:retry-token-2",NOW.plusSeconds(10),
    NOW.plusSeconds(100),1,1).await().indefinitely()).isEmpty();
 }

 private void markArtifactAbandoned(TenantId tenant,UUID productId,String version,Instant retentionUntil){
  try(Connection connection=runtime.getConnection()){
   connection.setAutoCommit(false);
   try(PreparedStatement context=connection.prepareStatement("SELECT set_config('numou.tenant_id',?,true)")){
    context.setString(1,tenant.value().toString());context.execute();
   }
   try(PreparedStatement update=connection.prepareStatement("""
    UPDATE developer_api_artifacts SET lifecycle_state='ABANDONED',retention_until=?,next_cleanup_at=?
    WHERE provider_tenant_id=? AND product_id=? AND api_version=?
    """)){
    update.setTimestamp(1,Timestamp.from(retentionUntil));update.setTimestamp(2,Timestamp.from(retentionUntil));
    update.setObject(3,tenant.value());update.setObject(4,productId);update.setString(5,version);
    assertThat(update.executeUpdate()).isEqualTo(1);
   }
   connection.commit();
  }catch(SQLException failure){throw new IllegalStateException(failure);}
 }

 @Test void commitsCatalogStateAndOutboxEventAtomically(){
  TenantId tenant=TenantId.generate();
  var context=new ExecutionContext(tenant,null,null,ActorId.of("developer-user"),"trace-api-catalog",null,NOW,null,null,null);
  var publisher=new PostgresApiCatalogEventPublisher(transactions,()->context,new ObjectMapper().registerModule(new JavaTimeModule()));
  ApiProduct committed=product(tenant,"atomic-commit");var committedEvents=committed.pullDomainEvents();
  transactions.execute(()->repository.save(tenant,committed,0).chain(()->publisher.publish(committedEvents))).await().indefinitely();
  assertThat(repository.findById(tenant,committed.id()).await().indefinitely()).isPresent();
  assertThat(outboxCount(tenant)).isEqualTo(1);

  ApiProduct rolledBack=product(tenant,"atomic-rollback");var rollbackEvents=rolledBack.pullDomainEvents();
  assertThatThrownBy(()->transactions.execute(()->repository.save(tenant,rolledBack,0)
    .chain(()->publisher.publish(rollbackEvents))
    .chain(()->Uni.createFrom().<Void>failure(new IllegalStateException("force rollback")))).await().indefinitely())
    .hasMessageContaining("force rollback");
  assertThat(repository.findById(tenant,rolledBack.id()).await().indefinitely()).isEmpty();
  assertThat(outboxCount(tenant)).isEqualTo(1);
 }


 private void assertPublishedArtifactCannotBeScheduledForDeletion(TenantId tenant,UUID productId,String version){
  try(Connection c=runtime.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement context=c.prepareStatement("SELECT set_config('numou.tenant_id',?,true)")){
    context.setString(1,tenant.value().toString());context.execute();
   }
   assertThatThrownBy(()->{
    try(PreparedStatement s=c.prepareStatement("""
     UPDATE developer_api_artifacts SET lifecycle_state='DELETE_PENDING'
     WHERE provider_tenant_id=? AND product_id=? AND api_version=?
     """)){
     s.setObject(1,tenant.value());s.setObject(2,productId);s.setString(3,version);s.executeUpdate();
    }
   }).isInstanceOf(SQLException.class).hasMessageContaining("Published API artifacts are protected");
   c.rollback();
  }catch(SQLException e){throw new IllegalStateException(e);}
 }
 private String artifactState(TenantId tenant,UUID productId,String version){
  try(Connection c=runtime.getConnection()){
   try(PreparedStatement context=c.prepareStatement("SELECT set_config('numou.tenant_id',?,false)")){
    context.setString(1,tenant.value().toString());context.execute();
   }
   try(PreparedStatement s=c.prepareStatement("""
    SELECT lifecycle_state FROM developer_api_artifacts
    WHERE provider_tenant_id=? AND product_id=? AND api_version=?
    """)){
    s.setObject(1,tenant.value());s.setObject(2,productId);s.setString(3,version);
    try(ResultSet rs=s.executeQuery()){if(!rs.next())throw new AssertionError("artifact lifecycle row missing");return rs.getString(1);}
   }
  }catch(SQLException e){throw new IllegalStateException(e);}
 }
 private long outboxCount(TenantId tenant){
  try(Connection c=runtime.getConnection();PreparedStatement s=c.prepareStatement("SELECT count(*) FROM platform_outbox WHERE tenant_id=?")){
   s.setObject(1,tenant.value());try(ResultSet rs=s.executeQuery()){rs.next();return rs.getLong(1);}
  }catch(SQLException e){throw new IllegalStateException(e);}
 }

 private static ApiProduct product(TenantId tenant,String slug){return ApiProduct.create(ApiProductId.newId(),tenant.value(),"API "+slug,slug,"test catalog",NOW);}
 private static ApiVersion version(String v){return ApiVersion.draft(v,new ApiSpecification("3.1","rustfs://api-specs/orders.yaml","a".repeat(64)),Set.of(new ApiScope("orders.read","Read orders")));}

 private static final class RoleDataSource implements DataSource{
  private final DataSource delegate;RoleDataSource(DataSource d){delegate=d;}
  public Connection getConnection()throws SQLException{Connection c=delegate.getConnection();try(Statement s=c.createStatement()){s.execute("SET ROLE api_catalog_runtime");return c;}catch(SQLException e){c.close();throw e;}}
  public Connection getConnection(String u,String p)throws SQLException{return getConnection();}
  public PrintWriter getLogWriter()throws SQLException{return delegate.getLogWriter();}
  public void setLogWriter(PrintWriter w)throws SQLException{delegate.setLogWriter(w);}
  public void setLoginTimeout(int t)throws SQLException{delegate.setLoginTimeout(t);}
  public int getLoginTimeout()throws SQLException{return delegate.getLoginTimeout();}
  public Logger getParentLogger(){return Logger.getLogger("api-catalog-test");}
  public <T>T unwrap(Class<T> c)throws SQLException{return delegate.unwrap(c);}
  public boolean isWrapperFor(Class<?> c)throws SQLException{return delegate.isWrapperFor(c);}
 }
}
