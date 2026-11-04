package tech.kayys.numou.developer.apicatalog.postgres;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
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
 PostgresApiProductRepository repository;
 static final Instant NOW=Instant.parse("2026-11-04T10:00:00Z");

 @BeforeAll static void migrate()throws Exception{
  owner=new PGSimpleDataSource();owner.setURL(POSTGRES.getJdbcUrl());owner.setUser(POSTGRES.getUsername());owner.setPassword(POSTGRES.getPassword());
  Flyway.configure().dataSource(owner).locations("classpath:db/migration/api-catalog").load().migrate();
  try(Connection c=owner.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE ROLE api_catalog_runtime NOLOGIN NOBYPASSRLS");
   s.execute("GRANT USAGE ON SCHEMA public TO api_catalog_runtime");
   s.execute("GRANT SELECT,INSERT,UPDATE ON developer_api_products,developer_api_versions,developer_api_scopes TO api_catalog_runtime");
  }
  runtime=new RoleDataSource(owner);
 }
 @BeforeEach void setup(){repository=new PostgresApiProductRepository(runtime);}

 @Test void roundTripsProductVersionsAndScopesWithoutEmittingCreationEventOnLoad(){
  TenantId tenant=TenantId.generate();ApiProduct product=product(tenant,"orders");
  repository.save(tenant,product,0).await().indefinitely();
  var loaded=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(loaded.revision()).isEqualTo(1);
  assertThat(loaded.pullDomainEvents()).isEmpty();
  loaded.addVersion(version("1.0.0"),NOW.plusSeconds(1));
  repository.save(tenant,loaded,1).await().indefinitely();
  var withVersion=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(withVersion.versions()).containsKey("1.0.0");
  assertThat(withVersion.versions().get("1.0.0").scopes()).containsKey("orders.read");
  withVersion.publishVersion("1.0.0",NOW.plusSeconds(2));
  repository.save(tenant,withVersion,2).await().indefinitely();
  var published=repository.findById(tenant,product.id()).await().indefinitely().orElseThrow();
  assertThat(published.status()).isEqualTo(ApiProductStatus.ACTIVE);
  assertThat(published.versions().get("1.0.0").status()).isEqualTo(ApiVersionStatus.PUBLISHED);
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
    .hasRootCauseInstanceOf(PostgresApiProductRepository.OptimisticLockException.class);
  assertThat(repository.findById(tenant,product.id()).await().indefinitely().orElseThrow().versions()).containsKey("1.0.0");
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
