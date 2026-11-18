package tech.kayys.numou.content.postgres;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.media.*;
import tech.kayys.numou.content.spi.ContentMediaReferenceRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
public final class PostgresContentMediaReferenceRepository implements ContentMediaReferenceRepository {
 private final DataSource dataSource;
 public PostgresContentMediaReferenceRepository(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}
 public boolean attach(ContentMediaReference r){String sql="INSERT INTO content_media_reference(tenant_id,reference_id,content_id,revision_number,media_asset_id,role,created_at) SELECT ?,?,?,?,?,?,? WHERE EXISTS (SELECT 1 FROM content_media_asset a WHERE a.tenant_id=? AND a.media_asset_id=? AND a.lifecycle_status='AVAILABLE' AND a.scan_status='CLEAN')";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,r.tenantId().value());p.setObject(2,r.id());p.setObject(3,r.contentId().value());p.setInt(4,r.revisionNumber());p.setObject(5,r.mediaAssetId().value());p.setString(6,r.role());p.setTimestamp(7,Timestamp.from(r.createdAt()));p.setObject(8,r.tenantId().value());p.setObject(9,r.mediaAssetId().value());return p.executeUpdate()==1;}
  catch(SQLException e){throw failure("attach media to content revision",e);}
 }
 public boolean detach(TenantId tenant,UUID id){String sql="DELETE FROM content_media_reference WHERE tenant_id=? AND reference_id=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setObject(2,id);return p.executeUpdate()==1;}
  catch(SQLException e){throw failure("detach media reference",e);}
 }
 public List<ContentMediaReference> findByContent(TenantId tenant,ContentId content,int revision){String sql="SELECT reference_id,media_asset_id,role,created_at FROM content_media_reference WHERE tenant_id=? AND content_id=? AND revision_number=? ORDER BY created_at,reference_id";List<ContentMediaReference> result=new ArrayList<>();
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setObject(2,content.value());p.setInt(3,revision);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new ContentMediaReference(r.getObject("reference_id",UUID.class),tenant,content,revision,new MediaAssetId(r.getObject("media_asset_id",UUID.class)),r.getString("role"),r.getTimestamp("created_at").toInstant()));}return List.copyOf(result);}
  catch(SQLException e){throw failure("find content media references",e);}
 }
 private RuntimeException failure(String op,SQLException e){return new IllegalStateException("Unable to "+op+" (SQLState="+e.getSQLState()+")",e);}
}
