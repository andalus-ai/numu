package tech.kayys.numou.content.postgres;
import tech.kayys.numou.content.domain.TenantId;
import tech.kayys.numou.content.domain.model.*;
import tech.kayys.numou.content.spi.ContentTypeRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** PostgreSQL persistence for tenant-scoped immutable schema snapshots. */
public final class PostgresContentTypeRepository implements ContentTypeRepository {
 private final DataSource dataSource;
 public PostgresContentTypeRepository(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource);}
 @Override public void createType(ContentTypeSchema s){
  if(s.version()!=1||s.status()!=SchemaStatus.DRAFT)throw new IllegalArgumentException("A content type must start with draft schema version 1");
  try(Connection c=dataSource.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement p=c.prepareStatement("INSERT INTO content_type(tenant_id,content_type_id,type_key,display_name,created_at) VALUES (?,?,?,?,?)")){
    p.setObject(1,s.tenantId().value());p.setObject(2,s.typeId().value());p.setString(3,s.key());p.setString(4,s.displayName());p.setTimestamp(5,Timestamp.from(s.createdAt()));p.executeUpdate();
   }
   insertSchema(c,s);c.commit();
  }catch(SQLException e){throw failure("create content type",e);}
 }
 @Override public void createVersion(ContentTypeSchema s){
  if(s.version()<2||s.status()!=SchemaStatus.DRAFT)throw new IllegalArgumentException("A new schema version must be a draft with version >= 2");
  try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);insertSchema(c,s);c.commit();}
  catch(SQLException e){throw failure("create content type schema version",e);}
 }
 private void insertSchema(Connection c,ContentTypeSchema s)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("INSERT INTO content_type_schema(tenant_id,content_type_id,schema_version,status,created_at,published_at) VALUES (?,?,?,?,?,NULL)")){
   p.setObject(1,s.tenantId().value());p.setObject(2,s.typeId().value());p.setInt(3,s.version());p.setString(4,s.status().name());p.setTimestamp(5,Timestamp.from(s.createdAt()));p.executeUpdate();
  }
  String fs="INSERT INTO content_type_field(tenant_id,content_type_id,schema_version,field_name,field_type,required,nullable,min_length,max_length,min_number,max_number,validation_pattern) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
  String os="INSERT INTO content_type_field_option(tenant_id,content_type_id,schema_version,field_name,option_value,option_order) VALUES (?,?,?,?,?,?)";
  try(PreparedStatement f=c.prepareStatement(fs);PreparedStatement o=c.prepareStatement(os)){
   for(ContentFieldDefinition d:s.fields()){
    f.setObject(1,s.tenantId().value());f.setObject(2,s.typeId().value());f.setInt(3,s.version());f.setString(4,d.name());f.setString(5,d.type().name());
    f.setBoolean(6,d.required());f.setBoolean(7,d.nullable());setInt(f,8,d.minLength());setInt(f,9,d.maxLength());
    setDecimal(f,10,d.minNumber());setDecimal(f,11,d.maxNumber());f.setString(12,d.pattern());f.addBatch();
    for(int i=0;i<d.allowedValues().size();i++){o.setObject(1,s.tenantId().value());o.setObject(2,s.typeId().value());o.setInt(3,s.version());o.setString(4,d.name());o.setString(5,d.allowedValues().get(i));o.setInt(6,i);o.addBatch();}
   }
   f.executeBatch();o.executeBatch();
  }
 }
 @Override public Optional<ContentTypeSchema> findVersion(TenantId tenant,ContentTypeId type,int version){
  String sql="SELECT t.type_key,t.display_name,s.status,s.created_at,s.published_at FROM content_type t JOIN content_type_schema s ON s.tenant_id=t.tenant_id AND s.content_type_id=t.content_type_id WHERE t.tenant_id=? AND t.content_type_id=? AND s.schema_version=?";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
   p.setObject(1,tenant.value());p.setObject(2,type.value());p.setInt(3,version);
   try(ResultSet r=p.executeQuery()){
    if(!r.next())return Optional.empty();
    String key=r.getString(1),name=r.getString(2);SchemaStatus status=SchemaStatus.valueOf(r.getString(3));
    Instant created=r.getTimestamp(4).toInstant();Timestamp pt=r.getTimestamp(5);Instant published=pt==null?null:pt.toInstant();
    return Optional.of(new ContentTypeSchema(tenant,type,key,name,version,status,loadFields(c,tenant,type,version),created,published));
   }
  }catch(SQLException e){throw failure("find content type schema",e);}
 }
 private List<ContentFieldDefinition> loadFields(Connection c,TenantId tenant,ContentTypeId type,int version)throws SQLException{
  String sql="SELECT field_name,field_type,required,nullable,min_length,max_length,min_number,max_number,validation_pattern FROM content_type_field WHERE tenant_id=? AND content_type_id=? AND schema_version=? ORDER BY field_name";
  List<ContentFieldDefinition> result=new ArrayList<>();
  try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant.value());p.setObject(2,type.value());p.setInt(3,version);
   try(ResultSet r=p.executeQuery()){while(r.next()){
    String name=r.getString(1);
    result.add(new ContentFieldDefinition(name,FieldType.valueOf(r.getString(2)),r.getBoolean(3),r.getBoolean(4),
      (Integer)r.getObject(5),(Integer)r.getObject(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getString(9),loadOptions(c,tenant,type,version,name)));
   }}
  }return result;
 }
 private List<String> loadOptions(Connection c,TenantId tenant,ContentTypeId type,int version,String field)throws SQLException{
  List<String> values=new ArrayList<>();
  try(PreparedStatement p=c.prepareStatement("SELECT option_value FROM content_type_field_option WHERE tenant_id=? AND content_type_id=? AND schema_version=? AND field_name=? ORDER BY option_order")){
   p.setObject(1,tenant.value());p.setObject(2,type.value());p.setInt(3,version);p.setString(4,field);
   try(ResultSet r=p.executeQuery()){while(r.next())values.add(r.getString(1));}
  }return values;
 }
 @Override public boolean publish(TenantId tenant,ContentTypeId type,int version,Instant at){
  String sql="UPDATE content_type_schema SET status='PUBLISHED',published_at=? WHERE tenant_id=? AND content_type_id=? AND schema_version=? AND status='DRAFT'";
  try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){
   p.setTimestamp(1,Timestamp.from(at));p.setObject(2,tenant.value());p.setObject(3,type.value());p.setInt(4,version);return p.executeUpdate()==1;
  }catch(SQLException e){throw failure("publish content type schema",e);}
 }
 private void setInt(PreparedStatement p,int i,Integer v)throws SQLException{if(v==null)p.setNull(i,Types.INTEGER);else p.setInt(i,v);}
 private void setDecimal(PreparedStatement p,int i,java.math.BigDecimal v)throws SQLException{if(v==null)p.setNull(i,Types.NUMERIC);else p.setBigDecimal(i,v);}
 private RuntimeException failure(String op,SQLException e){return new IllegalStateException("Unable to "+op+" (SQLState="+e.getSQLState()+")",e);}
}
