package tech.kayys.numou.developerplatform.postgres;
import tech.kayys.numou.developerplatform.application.DeveloperApplicationStore;
import tech.kayys.numou.developerplatform.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import javax.sql.DataSource;import java.sql.*;import java.util.*;import java.util.concurrent.*;
/** JDBC persistence with tenant predicates, transaction-local RLS and atomic audit/outbox writes. */
public final class PostgresDeveloperApplicationStore implements DeveloperApplicationStore{
 private final DataSource dataSource;
 public PostgresDeveloperApplicationStore(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}
 @Override public CompletionStage<CreateResult> registerIfAbsent(DeveloperApplication app,String keyHash,String fingerprint,DeveloperApplicationEvent event){
  try{hash(keyHash);hash(fingerprint);initial(app,event);
   try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{setTenant(c,app.tenantId());
    try(PreparedStatement s=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?,0))")){s.setString(1,app.tenantId().toString());s.setString(2,keyHash);s.execute();}
    try(PreparedStatement s=c.prepareStatement("SELECT request_fingerprint,application_id FROM developer_application_idempotency WHERE tenant_id=? AND idempotency_key_sha256=? FOR UPDATE")){
     s.setObject(1,app.tenantId().value());s.setString(2,keyHash);try(ResultSet rs=s.executeQuery()){if(rs.next()){
      if(!fingerprint.equals(rs.getString(1))){c.commit();return done(new CreateResult(CreateResult.Outcome.CONFLICT,Optional.empty()));}
      Optional<DeveloperApplication> existing=find(c,app.tenantId(),new DeveloperApplicationId(rs.getObject(2,UUID.class)));
      if(existing.isEmpty())throw new SQLException("idempotency record references missing application");c.commit();return done(new CreateResult(CreateResult.Outcome.REPLAYED,existing));
     }}
    }
    try(PreparedStatement s=c.prepareStatement("""
      INSERT INTO developer_applications(id,tenant_id,slug,name,description,status,revision,created_at,updated_at,created_by,updated_by)
      VALUES(?,?,?,?,?,'ACTIVE',1,?,?,?,?) ON CONFLICT(tenant_id,slug) DO NOTHING RETURNING id
      """)){s.setObject(1,app.id().value());s.setObject(2,app.tenantId().value());s.setString(3,app.slug());s.setString(4,app.name());s.setString(5,app.description());s.setTimestamp(6,Timestamp.from(app.createdAt()));s.setTimestamp(7,Timestamp.from(app.updatedAt()));s.setString(8,app.createdBy());s.setString(9,app.updatedBy());try(ResultSet rs=s.executeQuery()){if(!rs.next()){c.commit();return done(new CreateResult(CreateResult.Outcome.CONFLICT,Optional.empty()));}}}
    try(PreparedStatement s=c.prepareStatement("INSERT INTO developer_application_idempotency(tenant_id,idempotency_key_sha256,request_fingerprint,application_id,created_at) VALUES(?,?,?,?,?)")){s.setObject(1,app.tenantId().value());s.setString(2,keyHash);s.setString(3,fingerprint);s.setObject(4,app.id().value());s.setTimestamp(5,Timestamp.from(app.createdAt()));s.executeUpdate();}
    insertEvent(c,event);c.commit();return done(new CreateResult(CreateResult.Outcome.CREATED,Optional.of(app)));
   }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}}
  }catch(SQLException e){return CompletableFuture.failedFuture(storage("register",e));}
  catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
 }
 @Override public CompletionStage<Optional<DeveloperApplication>> find(TenantId tenant,DeveloperApplicationId id){
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);setTenant(c,tenant);var result=find(c,tenant,id);c.commit();return done(result);}catch(SQLException e){return CompletableFuture.failedFuture(storage("find",e));}
 }
 @Override public CompletionStage<List<DeveloperApplication>> list(TenantId tenant,UUID afterId,int limit){
  if(limit<1||limit>100)return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be 1-100"));
  String sql="SELECT id,tenant_id,slug,name,description,status,revision,created_at,updated_at,created_by,updated_by FROM developer_applications WHERE tenant_id=? AND (?::uuid IS NULL OR id>?) ORDER BY id LIMIT ?";
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);setTenant(c,tenant);List<DeveloperApplication> values=new ArrayList<>();try(PreparedStatement s=c.prepareStatement(sql)){s.setObject(1,tenant.value());s.setObject(2,afterId);s.setObject(3,afterId);s.setInt(4,limit);try(ResultSet rs=s.executeQuery()){while(rs.next())values.add(read(rs));}}c.commit();return done(List.copyOf(values));}catch(SQLException e){return CompletableFuture.failedFuture(storage("list",e));}
 }
 @Override public CompletionStage<Boolean> update(DeveloperApplication app,long expected,DeveloperApplicationEvent event){
  if(event.revision()!=expected+1||event.revision()!=app.revision()||!event.applicationId().equals(app.id())||!event.tenantId().equals(app.tenantId()))return CompletableFuture.failedFuture(new IllegalArgumentException("event does not match application revision"));
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);setTenant(c,app.tenantId());try(PreparedStatement s=c.prepareStatement("UPDATE developer_applications SET name=?,description=?,status=?,revision=?,updated_at=?,updated_by=? WHERE tenant_id=? AND id=? AND revision=?")){
   s.setString(1,app.name());s.setString(2,app.description());s.setString(3,app.status().name());s.setLong(4,app.revision());s.setTimestamp(5,Timestamp.from(app.updatedAt()));s.setString(6,app.updatedBy());s.setObject(7,app.tenantId().value());s.setObject(8,app.id().value());s.setLong(9,expected);
   if(s.executeUpdate()!=1){c.rollback();return done(false);}
  }insertEvent(c,event);c.commit();return done(true);
  }catch(SQLException e){return CompletableFuture.failedFuture(storage("update",e));}
 }
 private static Optional<DeveloperApplication> find(Connection c,TenantId tenant,DeveloperApplicationId id)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("SELECT id,tenant_id,slug,name,description,status,revision,created_at,updated_at,created_by,updated_by FROM developer_applications WHERE tenant_id=? AND id=?")){s.setObject(1,tenant.value());s.setObject(2,id.value());try(ResultSet rs=s.executeQuery()){return rs.next()?Optional.of(read(rs)):Optional.empty();}}
 }
 private static DeveloperApplication read(ResultSet rs)throws SQLException{return DeveloperApplication.reconstitute(new DeveloperApplicationId(rs.getObject("id",UUID.class)),TenantId.of(rs.getObject("tenant_id",UUID.class)),rs.getString("slug"),rs.getString("name"),rs.getString("description"),DeveloperApplicationStatus.valueOf(rs.getString("status")),rs.getLong("revision"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getString("created_by"),rs.getString("updated_by"));}
 private static void insertEvent(Connection c,DeveloperApplicationEvent e)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("INSERT INTO developer_application_events(event_id,tenant_id,application_id,event_type,revision,actor_id,correlation_id,occurred_at) VALUES(?,?,?,?,?,?,?,?)")){s.setObject(1,e.eventId());s.setObject(2,e.tenantId().value());s.setObject(3,e.applicationId().value());s.setString(4,e.eventType());s.setLong(5,e.revision());s.setString(6,e.actorId());s.setString(7,e.correlationId());s.setTimestamp(8,Timestamp.from(e.occurredAt()));s.executeUpdate();}
  try(PreparedStatement s=c.prepareStatement("INSERT INTO developer_application_outbox(event_id,tenant_id,application_id,event_type,revision,actor_id,correlation_id,occurred_at,available_at) VALUES(?,?,?,?,?,?,?,?,?)")){s.setObject(1,e.eventId());s.setObject(2,e.tenantId().value());s.setObject(3,e.applicationId().value());s.setString(4,e.eventType());s.setLong(5,e.revision());s.setString(6,e.actorId());s.setString(7,e.correlationId());s.setTimestamp(8,Timestamp.from(e.occurredAt()));s.setTimestamp(9,Timestamp.from(e.occurredAt()));s.executeUpdate();}
 }
 private static void initial(DeveloperApplication a,DeveloperApplicationEvent e){if(e.revision()!=1||!e.eventType().equals("DEVELOPER_APPLICATION_REGISTERED")||!e.tenantId().equals(a.tenantId())||!e.applicationId().equals(a.id())||!e.actorId().equals(a.createdBy())||!e.occurredAt().equals(a.createdAt()))throw new IllegalArgumentException("registration event mismatch");}
 private static void setTenant(Connection c,TenantId tenant)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT set_config('numou.tenant_id',?,true)")){s.setString(1,tenant.value().toString());s.execute();}}
 private static void hash(String s){if(s==null||!s.matches("^[a-f0-9]{64}$"))throw new IllegalArgumentException("expected SHA-256 hash");}
 private static void rollback(Connection c,Throwable e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}}
 private static IllegalStateException storage(String op,SQLException e){return new IllegalStateException("Unable to "+op+" developer application",e);}
 private static <T> CompletionStage<T> done(T value){return CompletableFuture.completedFuture(value);}
}