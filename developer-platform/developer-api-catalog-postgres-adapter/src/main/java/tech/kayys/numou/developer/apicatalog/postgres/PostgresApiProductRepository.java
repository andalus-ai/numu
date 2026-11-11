package tech.kayys.numou.developer.apicatalog.postgres;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiProductRepository;
import tech.kayys.numou.developer.apicatalog.domain.*;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.*;
import java.util.*;

/**
 * JDBC implementation of the API catalog aggregate repository.
 * Operations install the trusted tenant in transaction-local PostgreSQL state and carry
 * the tenant predicate in every statement. Calls inside UnitOfWork reuse its connection.
 */
@ApplicationScoped
public final class PostgresApiProductRepository implements ApiProductRepository {
 private final ApiCatalogJdbcTransactionManager transactions;
 @Inject public PostgresApiProductRepository(ApiCatalogJdbcTransactionManager transactions){this.transactions=Objects.requireNonNull(transactions,"transactions cannot be null");}
 public PostgresApiProductRepository(DataSource dataSource){this(new ApiCatalogJdbcTransactionManager(dataSource));}

 @Override public Uni<Optional<ApiProduct>> findById(TenantId tenant,ApiProductId id){
  Objects.requireNonNull(id,"id cannot be null");
  return inTenant(tenant,c->load(c,tenant.value(),id.value()));
 }
 @Override public Uni<Optional<ApiProduct>> findBySlug(TenantId tenant,String slug){
  Objects.requireNonNull(slug,"slug cannot be null");
  return inTenant(tenant,c->{
   try(PreparedStatement s=c.prepareStatement("SELECT id FROM developer_api_products WHERE provider_tenant_id=? AND slug=?")){
    s.setObject(1,tenant.value());s.setString(2,slug.trim().toLowerCase(Locale.ROOT));
    try(ResultSet rs=s.executeQuery()){return rs.next()?load(c,tenant.value(),rs.getObject(1,UUID.class)):Optional.empty();}
   }
  });
 }
 @Override public Uni<Page<ApiProduct>> findAll(TenantId tenant,PageRequest request){
  Objects.requireNonNull(request,"request cannot be null");
  return inTenant(tenant,c->{
   long total;
   try(PreparedStatement s=c.prepareStatement("SELECT count(*) FROM developer_api_products WHERE provider_tenant_id=?")){
    s.setObject(1,tenant.value());try(ResultSet rs=s.executeQuery()){rs.next();total=rs.getLong(1);}
   }
   List<ApiProduct> values=new ArrayList<>();
   try(PreparedStatement s=c.prepareStatement("SELECT id FROM developer_api_products WHERE provider_tenant_id=? ORDER BY updated_at DESC,id ASC LIMIT ? OFFSET ?")){
    s.setObject(1,tenant.value());s.setInt(2,request.size());s.setLong(3,request.offset());
    try(ResultSet rs=s.executeQuery()){while(rs.next()){var product=load(c,tenant.value(),rs.getObject(1,UUID.class));product.ifPresent(values::add);}}
   }
   return Page.of(values,total,request);
  });
 }
 @Override public Uni<Void> save(TenantId tenant,ApiProduct product,long expectedRevision){
  Objects.requireNonNull(product,"product cannot be null");
  if(!tenant.value().equals(product.providerTenantId()))return Uni.createFrom().failure(new IllegalArgumentException("repository tenant does not own API product"));
  if(expectedRevision<0 || product.revision()!=expectedRevision+1)return Uni.createFrom().failure(new IllegalArgumentException("product revision must equal expected revision + 1"));
  return inTenant(tenant,c->{
   if(expectedRevision==0)insertProduct(c,tenant.value(),product);
   else updateProduct(c,tenant.value(),product,expectedRevision);
   for(ApiVersion version:product.versions().values())persistVersion(c,tenant.value(),product.id().value(),version);
   return null;
  });
 }

 private <T> Uni<T> inTenant(TenantId tenant,SqlWork<T> work){
  Objects.requireNonNull(tenant,"tenant cannot be null");
  return transactions.withTenant(tenant,connection->work.apply(connection));
 }

 private static void insertProduct(Connection c,UUID tenant,ApiProduct p)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("""
   INSERT INTO developer_api_products(provider_tenant_id,id,slug,name,description,status,revision,created_at,updated_at)
   VALUES(?,?,?,?,?,?,?,?,?)
   """)){
   s.setObject(1,tenant);s.setObject(2,p.id().value());s.setString(3,p.slug());s.setString(4,p.name());s.setString(5,p.description());
   s.setString(6,p.status().name());s.setLong(7,p.revision());s.setTimestamp(8,Timestamp.from(p.createdAt()));s.setTimestamp(9,Timestamp.from(p.updatedAt()));
   s.executeUpdate();
  }
 }
 private static void updateProduct(Connection c,UUID tenant,ApiProduct p,long expected)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("""
   UPDATE developer_api_products SET status=?,revision=?,updated_at=?
   WHERE provider_tenant_id=? AND id=? AND revision=?
   """)){
   s.setString(1,p.status().name());s.setLong(2,p.revision());s.setTimestamp(3,Timestamp.from(p.updatedAt()));
   s.setObject(4,tenant);s.setObject(5,p.id().value());s.setLong(6,expected);
   if(s.executeUpdate()!=1)throw new OptimisticLockException("API product was changed or is not visible in this tenant");
  }
 }
 private static void persistVersion(Connection c,UUID tenant,UUID productId,ApiVersion v)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("""
   INSERT INTO developer_api_versions(provider_tenant_id,product_id,version,openapi_version,artifact_reference,sha256,status)
   VALUES(?,?,?,?,?,?,?) ON CONFLICT(provider_tenant_id,product_id,version) DO NOTHING
   """)){
   s.setObject(1,tenant);s.setObject(2,productId);s.setString(3,v.version());s.setString(4,v.specification().openApiVersion());
   s.setString(5,v.specification().artifactReference());s.setString(6,v.specification().sha256());s.setString(7,v.status().name());s.executeUpdate();
  }
  try(PreparedStatement s=c.prepareStatement("""
   UPDATE developer_api_versions SET status=?
   WHERE provider_tenant_id=? AND product_id=? AND version=? AND openapi_version=? AND artifact_reference=? AND sha256=?
   """)){
   s.setString(1,v.status().name());s.setObject(2,tenant);s.setObject(3,productId);s.setString(4,v.version());
   s.setString(5,v.specification().openApiVersion());s.setString(6,v.specification().artifactReference());s.setString(7,v.specification().sha256());
   if(s.executeUpdate()!=1)throw new IllegalStateException("persisted API version contract differs from immutable aggregate version");
  }
  persistArtifactLifecycle(c,tenant,productId,v);
  for(ApiScope scope:v.scopes().values()){
   try(PreparedStatement s=c.prepareStatement("""
    INSERT INTO developer_api_scopes(provider_tenant_id,product_id,api_version,scope_name,description)
    VALUES(?,?,?,?,?) ON CONFLICT(provider_tenant_id,product_id,api_version,scope_name) DO NOTHING
    """)){
    s.setObject(1,tenant);s.setObject(2,productId);s.setString(3,v.version());s.setString(4,scope.name());s.setString(5,scope.description());s.executeUpdate();
   }
   try(PreparedStatement s=c.prepareStatement("""
    SELECT description FROM developer_api_scopes
    WHERE provider_tenant_id=? AND product_id=? AND api_version=? AND scope_name=?
    """)){
    s.setObject(1,tenant);s.setObject(2,productId);s.setString(3,v.version());s.setString(4,scope.name());
    try(ResultSet rs=s.executeQuery()){if(!rs.next()||!scope.description().equals(rs.getString(1)))throw new IllegalStateException("persisted API scope differs from immutable aggregate scope");}
   }
  }
 }

 private static void persistArtifactLifecycle(Connection c,UUID tenant,UUID productId,ApiVersion version)throws SQLException{
  String state=version.status()==ApiVersionStatus.PUBLISHED?"PUBLISHED":"REGISTERED";
  try(PreparedStatement s=c.prepareStatement("""
   INSERT INTO developer_api_artifacts(provider_tenant_id,product_id,api_version,artifact_reference,sha256,lifecycle_state)
   VALUES(?,?,?,?,?,?)
   ON CONFLICT(provider_tenant_id,product_id,api_version) DO UPDATE
     SET lifecycle_state=CASE
       WHEN developer_api_artifacts.lifecycle_state='PUBLISHED' OR EXCLUDED.lifecycle_state='PUBLISHED'
         THEN 'PUBLISHED'
       ELSE developer_api_artifacts.lifecycle_state
     END
   WHERE developer_api_artifacts.artifact_reference=EXCLUDED.artifact_reference
     AND developer_api_artifacts.sha256=EXCLUDED.sha256
   """)){
   s.setObject(1,tenant);s.setObject(2,productId);s.setString(3,version.version());
   s.setString(4,version.specification().artifactReference());s.setString(5,version.specification().sha256());
   s.setString(6,state);
   if(s.executeUpdate()!=1)throw new IllegalStateException("artifact lifecycle identity differs from immutable API version");
  }
 }
 private static Optional<ApiProduct> load(Connection c,UUID tenant,UUID id)throws SQLException{
  String name,slug,description;ApiProductStatus status;long revision;java.time.Instant created,updated;
  try(PreparedStatement s=c.prepareStatement("""
   SELECT name,slug,description,status,revision,created_at,updated_at
   FROM developer_api_products WHERE provider_tenant_id=? AND id=?
   """)){
   s.setObject(1,tenant);s.setObject(2,id);
   try(ResultSet rs=s.executeQuery()){
    if(!rs.next())return Optional.empty();
    name=rs.getString("name");slug=rs.getString("slug");description=rs.getString("description");
    status=ApiProductStatus.valueOf(rs.getString("status"));revision=rs.getLong("revision");
    created=rs.getTimestamp("created_at").toInstant();updated=rs.getTimestamp("updated_at").toInstant();
   }
  }
  List<ApiVersion> versions=new ArrayList<>();
  try(PreparedStatement s=c.prepareStatement("""
   SELECT version,openapi_version,artifact_reference,sha256,status
   FROM developer_api_versions WHERE provider_tenant_id=? AND product_id=? ORDER BY version
   """)){
   s.setObject(1,tenant);s.setObject(2,id);
   try(ResultSet rs=s.executeQuery()){
    while(rs.next()){
     String version=rs.getString("version");ApiSpecification spec=new ApiSpecification(rs.getString("openapi_version"),rs.getString("artifact_reference"),rs.getString("sha256"));
     ApiVersionStatus versionStatus=ApiVersionStatus.valueOf(rs.getString("status"));
     Map<String,ApiScope> scopes=new TreeMap<>();
     try(PreparedStatement scopeQuery=c.prepareStatement("""
      SELECT scope_name,description FROM developer_api_scopes
      WHERE provider_tenant_id=? AND product_id=? AND api_version=? ORDER BY scope_name
      """)){
      scopeQuery.setObject(1,tenant);scopeQuery.setObject(2,id);scopeQuery.setString(3,version);
      try(ResultSet sr=scopeQuery.executeQuery()){while(sr.next()){ApiScope scope=new ApiScope(sr.getString(1),sr.getString(2));scopes.put(scope.name(),scope);}}
     }
     versions.add(new ApiVersion(version,spec,scopes,versionStatus));
    }
   }
  }
  return Optional.of(ApiProduct.reconstitute(ApiProductId.of(id),tenant,name,slug,description,status,created,updated,revision,versions));
 }
 @FunctionalInterface private interface SqlWork<T>{T apply(Connection connection)throws SQLException;}
 public static final class OptimisticLockException extends RuntimeException{
  public OptimisticLockException(String message){super(message);}
  public OptimisticLockException(String message,Throwable cause){super(message,cause);}
 }
}
