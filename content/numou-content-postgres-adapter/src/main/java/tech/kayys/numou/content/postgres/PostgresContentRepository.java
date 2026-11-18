package tech.kayys.numou.content.postgres;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.model.ContentTypeId;
import tech.kayys.numou.content.spi.ContentRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** Tenant-scoped JDBC persistence. Revision payload and schema identity are appended atomically. */
public final class PostgresContentRepository implements ContentRepository {
 private final DataSource dataSource;
 public PostgresContentRepository(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}
 @Override public void create(ContentEntry entry){
  String head="INSERT INTO content_entry(tenant_id,content_id,status,current_revision,created_at,updated_at) VALUES (?,?,?,?,?,?)";
  String rev="INSERT INTO content_revision(tenant_id,content_id,revision_number,title,slug,body,author_id,created_at,content_type_id,schema_version) VALUES (?,?,?,?,?,?,?,?,?,?)";
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);
   try(PreparedStatement p=c.prepareStatement(head)){p.setObject(1,entry.tenantId().value());p.setObject(2,entry.id().value());p.setString(3,entry.status().name());p.setInt(4,entry.currentRevisionNumber());p.setTimestamp(5,Timestamp.from(entry.createdAt()));p.setTimestamp(6,Timestamp.from(entry.updatedAt()));p.executeUpdate();}
   try(PreparedStatement p=c.prepareStatement(rev)){bindRevision(p,entry,entry.currentRevision());p.executeUpdate();}
   c.commit();
  }catch(SQLException e){throw failure("create content",e);}
 }
 @Override public Optional<ContentEntry> find(TenantId tenantId,ContentId contentId){
  String sql="SELECT status,current_revision,created_at,updated_at FROM content_entry WHERE tenant_id=? AND content_id=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
   p.setObject(1,tenantId.value());p.setObject(2,contentId.value());
   try(ResultSet r=p.executeQuery()){if(!r.next())return Optional.empty();
    ContentStatus status=ContentStatus.valueOf(r.getString("status"));int pointer=r.getInt("current_revision");
    Instant created=r.getTimestamp("created_at").toInstant(),updated=r.getTimestamp("updated_at").toInstant();
    List<ContentRevision> revisions=loadRevisions(c,tenantId,contentId);
    return Optional.of(ContentEntry.rehydrate(contentId,tenantId,status,revisions,created,updated,pointer));
   }
  }catch(SQLException e){throw failure("find content",e);}
 }
 @Override public boolean appendRevision(ContentEntry revised,int expectedRevision){
  String update="UPDATE content_entry SET current_revision=?,updated_at=? WHERE tenant_id=? AND content_id=? AND status='DRAFT' AND current_revision=?";
  String insert="INSERT INTO content_revision(tenant_id,content_id,revision_number,title,slug,body,author_id,created_at,content_type_id,schema_version) VALUES (?,?,?,?,?,?,?,?,?,?)";
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);
   try(PreparedStatement p=c.prepareStatement(update)){p.setInt(1,revised.currentRevisionNumber());p.setTimestamp(2,Timestamp.from(revised.updatedAt()));p.setObject(3,revised.tenantId().value());p.setObject(4,revised.id().value());p.setInt(5,expectedRevision);if(p.executeUpdate()!=1){c.rollback();return false;}}
   try(PreparedStatement p=c.prepareStatement(insert)){bindRevision(p,revised,revised.currentRevision());p.executeUpdate();}
   c.commit();return true;
  }catch(SQLException e){throw failure("append content revision",e);}
 }
 @Override public boolean transitionStatus(TenantId tenantId,ContentId contentId,ContentStatus expected,ContentStatus target,Instant updatedAt){
  String sql="UPDATE content_entry SET status=?,updated_at=? WHERE tenant_id=? AND content_id=? AND status=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setString(1,target.name());p.setTimestamp(2,Timestamp.from(updatedAt));p.setObject(3,tenantId.value());p.setObject(4,contentId.value());p.setString(5,expected.name());return p.executeUpdate()==1;}
  catch(SQLException e){throw failure("transition content lifecycle",e);}
 }
 private List<ContentRevision> loadRevisions(Connection c,TenantId tenant,ContentId id)throws SQLException{
  List<ContentRevision> result=new ArrayList<>();
  String sql="SELECT revision_number,title,slug,body,author_id,created_at,content_type_id,schema_version FROM content_revision WHERE tenant_id=? AND content_id=? ORDER BY revision_number";
  try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setObject(2,id.value());
   try(ResultSet r=p.executeQuery()){while(r.next()){
    UUID typeUuid=r.getObject(7,UUID.class);int schemaVersion=r.getInt(8);if(r.wasNull())schemaVersion=0;
    result.add(new ContentRevision(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),
      typeUuid==null?null:new ContentTypeId(typeUuid),schemaVersion,new ActorId(r.getObject(5,UUID.class)),r.getTimestamp(6).toInstant()));
   }}
  }return result;
 }
 private void bindRevision(PreparedStatement p,ContentEntry e,ContentRevision r)throws SQLException{
  p.setObject(1,e.tenantId().value());p.setObject(2,e.id().value());p.setInt(3,r.number());p.setString(4,r.title());p.setString(5,r.slug());p.setString(6,r.body());p.setObject(7,r.authorId().value());p.setTimestamp(8,Timestamp.from(r.createdAt()));
  if(r.contentTypeId()==null)p.setNull(9,Types.OTHER);else p.setObject(9,r.contentTypeId().value());
  if(r.schemaVersion()==0)p.setNull(10,Types.INTEGER);else p.setInt(10,r.schemaVersion());
 }
 private RuntimeException failure(String operation,SQLException e){return new IllegalStateException("Unable to "+operation+" (SQLState="+e.getSQLState()+")",e);}
}
