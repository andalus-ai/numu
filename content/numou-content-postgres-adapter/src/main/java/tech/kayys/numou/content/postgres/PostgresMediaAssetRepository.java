package tech.kayys.numou.content.postgres;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.media.*;
import tech.kayys.numou.content.spi.MediaAssetRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public final class PostgresMediaAssetRepository implements MediaAssetRepository {
 private final DataSource dataSource;
 public PostgresMediaAssetRepository(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}
 public void create(MediaAsset a){String sql="INSERT INTO content_media_asset(tenant_id,media_asset_id,document_id,document_version_id,file_name,content_type,media_kind,byte_size,sha256,scan_status,lifecycle_status,alt_text,caption,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){int i=1;p.setObject(i++,a.tenantId().value());p.setObject(i++,a.id().value());p.setString(i++,a.documentId());p.setString(i++,a.documentVersionId());p.setString(i++,a.fileName());p.setString(i++,a.contentType());p.setString(i++,a.kind().name());p.setLong(i++,a.byteSize());p.setString(i++,a.sha256());p.setString(i++,a.scanStatus().name());p.setString(i++,a.status().name());p.setString(i++,a.altText());p.setString(i++,a.caption());p.setString(i++,a.createdBy());p.setTimestamp(i++,Timestamp.from(a.createdAt()));p.setTimestamp(i,Timestamp.from(a.updatedAt()));p.executeUpdate();}
  catch(SQLException e){throw failure("create media asset",e);}
 }
 public Optional<MediaAsset> find(TenantId tenant,MediaAssetId id){String sql="SELECT * FROM content_media_asset WHERE tenant_id=? AND media_asset_id=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setObject(2,id.value());try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}
  catch(SQLException e){throw failure("find media asset",e);}
 }
 public Optional<MediaAsset> findByDocumentVersion(TenantId tenant,String documentId,String versionId){String sql="SELECT * FROM content_media_asset WHERE tenant_id=? AND document_id=? AND document_version_id=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setString(2,documentId);p.setString(3,versionId);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}catch(SQLException e){throw failure("find media by document version",e);}
 }
 public boolean update(MediaAsset a,MediaScanStatus expectedScan,MediaLifecycleStatus expectedStatus){String sql="UPDATE content_media_asset SET scan_status=?,lifecycle_status=?,alt_text=?,caption=?,width=?,height=?,duration_millis=?,updated_at=? WHERE tenant_id=? AND media_asset_id=? AND scan_status=? AND lifecycle_status=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){int i=1;p.setString(i++,a.scanStatus().name());p.setString(i++,a.status().name());p.setString(i++,a.altText());p.setString(i++,a.caption());setInteger(p,i++,a.width());setInteger(p,i++,a.height());if(a.durationMillis()==null)p.setNull(i++,Types.BIGINT);else p.setLong(i++,a.durationMillis());p.setTimestamp(i++,Timestamp.from(a.updatedAt()));p.setObject(i++,a.tenantId().value());p.setObject(i++,a.id().value());p.setString(i++,expectedScan.name());p.setString(i,expectedStatus.name());return p.executeUpdate()==1;}
  catch(SQLException e){throw failure("update media asset",e);}
 }
 private MediaAsset map(ResultSet r)throws SQLException{return MediaAsset.rehydrate(new MediaAssetId(r.getObject("media_asset_id",UUID.class)),new TenantId(r.getObject("tenant_id",UUID.class)),r.getString("document_id"),r.getString("document_version_id"),r.getString("file_name"),r.getString("content_type"),MediaKind.valueOf(r.getString("media_kind")),r.getLong("byte_size"),r.getString("sha256"),(Integer)r.getObject("width"),(Integer)r.getObject("height"),(Long)r.getObject("duration_millis"),MediaScanStatus.valueOf(r.getString("scan_status")),MediaLifecycleStatus.valueOf(r.getString("lifecycle_status")),r.getString("alt_text"),r.getString("caption"),r.getString("created_by"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());}
 private void setInteger(PreparedStatement p,int i,Integer v)throws SQLException{if(v==null)p.setNull(i,Types.INTEGER);else p.setInt(i,v);}
 private RuntimeException failure(String op,SQLException e){return new IllegalStateException("Unable to "+op+" (SQLState="+e.getSQLState()+")",e);}
}
