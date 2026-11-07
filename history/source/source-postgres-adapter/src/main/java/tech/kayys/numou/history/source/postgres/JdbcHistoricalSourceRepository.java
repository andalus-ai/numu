package tech.kayys.numou.history.source.postgres;

import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL adapter. Every operation establishes transaction-local tenant RLS context. */
public final class JdbcHistoricalSourceRepository implements HistoricalSourceRepository {
 private final DataSource dataSource;
 public JdbcHistoricalSourceRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource); }

 @Override public boolean register(UUID tenant,HistoricalSource source) {
  Objects.requireNonNull(tenant); Objects.requireNonNull(source);
  return write(tenant,c->{
   try(PreparedStatement p=c.prepareStatement("insert into historical_source(tenant_id,source_id,current_revision,status,created_at,updated_at) values (?,?,1,?,?,?)")){
    p.setObject(1,tenant);p.setObject(2,source.id().value());p.setString(3,source.status().name());p.setTimestamp(4,Timestamp.from(source.createdAt()));p.setTimestamp(5,Timestamp.from(source.updatedAt()));p.executeUpdate();
   }
   insertRevision(c,tenant,source); return true;
  },false);
 }
 @Override public Optional<HistoricalSource> find(UUID tenant,HistoricalSourceId id) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(id);
  return read(tenant,c->{
   String sql="select r.source_id,r.kind,r.title,r.subtitle,r.description,r.canonical_uri,r.publisher,r.publication_date_text,r.language_tag,r.created_at,r.updated_at,r.revision,r.status from historical_source s join historical_source_revision r on r.tenant_id=s.tenant_id and r.source_id=s.source_id and r.revision=s.current_revision where s.tenant_id=? and s.source_id=?";
   try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant);p.setObject(2,id.value());try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(mapSource(rs)):Optional.empty();}}
  });
 }
 @Override public List<HistoricalSource> list(UUID tenant,int limit) {
  Objects.requireNonNull(tenant);if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");
  return read(tenant,c->{
   String sql="select r.source_id,r.kind,r.title,r.subtitle,r.description,r.canonical_uri,r.publisher,r.publication_date_text,r.language_tag,r.created_at,r.updated_at,r.revision,r.status from historical_source s join historical_source_revision r on r.tenant_id=s.tenant_id and r.source_id=s.source_id and r.revision=s.current_revision where s.tenant_id=? order by s.updated_at desc,s.source_id limit ?";
   try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,tenant);p.setInt(2,limit);try(ResultSet rs=p.executeQuery()){List<HistoricalSource> out=new ArrayList<>();while(rs.next())out.add(mapSource(rs));return List.copyOf(out);}}
  });
 }
 @Override public boolean revise(UUID tenant,HistoricalSource source,long expectedRevision) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(source);if(source.revision()!=expectedRevision+1)throw new IllegalArgumentException("revised source must advance exactly one revision");
  return write(tenant,c->{
   try(PreparedStatement p=c.prepareStatement("update historical_source set current_revision=?,status=?,updated_at=? where tenant_id=? and source_id=? and current_revision=? and status='ACTIVE'")){
    p.setLong(1,source.revision());p.setString(2,source.status().name());p.setTimestamp(3,Timestamp.from(source.updatedAt()));p.setObject(4,tenant);p.setObject(5,source.id().value());p.setLong(6,expectedRevision);
    if(p.executeUpdate()!=1)return false;
   }
   insertRevision(c,tenant,source);return true;
  },false);
 }
 @Override public boolean createClaim(UUID tenant,HistoricalClaim claim) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(claim);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_claim(tenant_id,claim_id,subject_kind,subject_id,statement,created_at,revision) values (?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,claim.id().value());p.setString(3,claim.subjectKind());p.setString(4,claim.subjectId());p.setString(5,claim.statement());p.setTimestamp(6,Timestamp.from(claim.createdAt()));p.setLong(7,claim.revision());p.executeUpdate();return true;
  }},false);
 }
 @Override public Optional<HistoricalClaim> findClaim(UUID tenant,HistoricalClaimId id) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(id);
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select claim_id,subject_kind,subject_id,statement,created_at,revision from historical_claim where tenant_id=? and claim_id=?")){
   p.setObject(1,tenant);p.setObject(2,id.value());try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(new HistoricalClaim(new HistoricalClaimId(rs.getObject(1,UUID.class)),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getLong(6))):Optional.empty();}
  }});
 }
 @Override public boolean appendEvidence(UUID tenant,ClaimEvidence evidence) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(evidence);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_claim_evidence(tenant_id,evidence_id,claim_id,source_id,relation,locator_type,locator,excerpt,recorded_at) values (?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,evidence.id());p.setObject(3,evidence.claimId().value());p.setObject(4,evidence.sourceId().value());p.setString(5,evidence.relation().name());p.setString(6,evidence.locatorType().name());p.setString(7,evidence.locator());p.setString(8,evidence.excerpt());p.setTimestamp(9,Timestamp.from(evidence.recordedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<ClaimEvidence> listEvidence(UUID tenant,HistoricalClaimId claimId,int limit) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(claimId);if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select evidence_id,claim_id,source_id,relation,locator_type,locator,excerpt,recorded_at from historical_claim_evidence where tenant_id=? and claim_id=? order by recorded_at,evidence_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,claimId.value());p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<ClaimEvidence> out=new ArrayList<>();while(rs.next())out.add(new ClaimEvidence(rs.getObject(1,UUID.class),new HistoricalClaimId(rs.getObject(2,UUID.class)),new HistoricalSourceId(rs.getObject(3,UUID.class)),EvidenceRelation.valueOf(rs.getString(4)),EvidenceLocatorType.valueOf(rs.getString(5)),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()));return List.copyOf(out);}
  }});
 }
 private static void insertRevision(Connection c,UUID tenant,HistoricalSource s)throws SQLException{
  try(PreparedStatement p=c.prepareStatement("insert into historical_source_revision(tenant_id,source_id,revision,kind,status,title,subtitle,description,canonical_uri,publisher,publication_date_text,language_tag,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,s.id().value());p.setLong(3,s.revision());p.setString(4,s.kind().name());p.setString(5,s.status().name());p.setString(6,s.title());p.setString(7,s.subtitle());p.setString(8,s.description());p.setString(9,s.canonicalUri());p.setString(10,s.publisher());p.setString(11,s.publicationDateText());p.setString(12,s.languageTag());p.setTimestamp(13,Timestamp.from(s.createdAt()));p.setTimestamp(14,Timestamp.from(s.updatedAt()));p.executeUpdate();
  }
 }
 private static HistoricalSource mapSource(ResultSet rs)throws SQLException{
  return new HistoricalSource(new HistoricalSourceId(rs.getObject(1,UUID.class)),HistoricalSourceKind.valueOf(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getTimestamp(10).toInstant(),rs.getTimestamp(11).toInstant(),rs.getLong(12),HistoricalSourceStatus.valueOf(rs.getString(13)));
 }
 @FunctionalInterface private interface SqlWork<T>{T run(Connection c)throws SQLException;}
 private <T>T read(UUID tenant,SqlWork<T> work){return execute(tenant,work,true);}
 private <T>T write(UUID tenant,SqlWork<T> work,boolean ignored){return execute(tenant,work,false);}
 private <T>T execute(UUID tenant,SqlWork<T> work,boolean readOnly){
  try(Connection c=dataSource.getConnection()){
   c.setAutoCommit(false);c.setReadOnly(readOnly);
   try(PreparedStatement p=c.prepareStatement("select set_config('numou.tenant_id', ?, true)")){p.setString(1,tenant.toString());p.execute();}
   try{T result=work.run(c);c.commit();return result;}catch(SQLException e){c.rollback();if("23505".equals(e.getSQLState()))return (T) Boolean.FALSE;throw e;}
  }catch(SQLException e){throw new IllegalStateException("Historical source persistence failed",e);}
 }
}