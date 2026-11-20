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
 @Override public boolean appendAssessment(UUID tenant,SourceAssessment assessment) {
  Objects.requireNonNull(tenant); Objects.requireNonNull(assessment);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_source_assessment(tenant_id,assessment_id,source_id,dimension,conclusion,rationale,methodology_id,methodology_version,assessor_ref,assessed_at) values (?,?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,assessment.id());p.setObject(3,assessment.sourceId().value());p.setString(4,assessment.dimension().name());p.setString(5,assessment.conclusion().name());p.setString(6,assessment.rationale());p.setString(7,assessment.methodologyId());p.setString(8,assessment.methodologyVersion());p.setString(9,assessment.assessorRef());p.setTimestamp(10,Timestamp.from(assessment.assessedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<SourceAssessment> listAssessments(UUID tenant,HistoricalSourceId sourceId,int limit) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(sourceId);if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select assessment_id,source_id,dimension,conclusion,rationale,methodology_id,methodology_version,assessor_ref,assessed_at from historical_source_assessment where tenant_id=? and source_id=? order by assessed_at,assessment_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<SourceAssessment> out=new ArrayList<>();while(rs.next())out.add(mapAssessment(rs));return List.copyOf(out);}
  }});
 }
 @Override public Optional<SourceAssessment> findAssessment(UUID tenant,UUID assessmentId) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(assessmentId);
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select assessment_id,source_id,dimension,conclusion,rationale,methodology_id,methodology_version,assessor_ref,assessed_at from historical_source_assessment where tenant_id=? and assessment_id=?")){
   p.setObject(1,tenant);p.setObject(2,assessmentId);try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(mapAssessment(rs)):Optional.empty();}
  }});
 }
 @Override public boolean appendAssessmentReview(UUID tenant,SourceAssessmentReview review) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(review);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_source_assessment_review(tenant_id,review_id,assessment_id,decision,rationale,reviewer_ref,reviewed_at) values (?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,review.id());p.setObject(3,review.assessmentId());p.setString(4,review.decision().name());p.setString(5,review.rationale());p.setString(6,review.reviewerRef());p.setTimestamp(7,Timestamp.from(review.reviewedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<SourceAssessmentReview> listAssessmentReviews(UUID tenant,UUID assessmentId,int limit) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(assessmentId);if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select review_id,assessment_id,decision,rationale,reviewer_ref,reviewed_at from historical_source_assessment_review where tenant_id=? and assessment_id=? order by reviewed_at,review_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,assessmentId);p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<SourceAssessmentReview> out=new ArrayList<>();while(rs.next())out.add(new SourceAssessmentReview(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),SourceAssessmentReviewDecision.valueOf(rs.getString(3)),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()));return List.copyOf(out);}
  }});
 }
 @Override public boolean appendContributor(UUID tenant,SourceContributor x) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(x);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_source_contributor(tenant_id,contributor_id,source_id,display_name,authority_ref,role,sequence_no,attribution_note) values (?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,x.id());p.setObject(3,x.sourceId().value());p.setString(4,x.displayName());p.setString(5,x.authorityRef());p.setString(6,x.role().name());p.setInt(7,x.sequence());p.setString(8,x.attributionNote());p.executeUpdate();return true;
  }},false);
 }
 @Override public List<SourceContributor> listContributors(UUID tenant,HistoricalSourceId sourceId,int limit) {
  checkLimit(limit);return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select contributor_id,source_id,display_name,authority_ref,role,sequence_no,attribution_note from historical_source_contributor where tenant_id=? and source_id=? order by sequence_no,contributor_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<SourceContributor> out=new ArrayList<>();while(rs.next())out.add(new SourceContributor(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),rs.getString(3),rs.getString(4),SourceContributorRole.valueOf(rs.getString(5)),rs.getInt(6),rs.getString(7)));return List.copyOf(out);}
  }});
 }
 @Override public boolean appendEdition(UUID tenant,SourceEdition x) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(x);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_source_edition(tenant_id,edition_id,source_id,edition_designation,publisher,publication_place,publication_date_text,language_tag,identifier,canonical_uri,notes,recorded_at) values (?,?,?,?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,x.id());p.setObject(3,x.sourceId().value());p.setString(4,x.editionDesignation());p.setString(5,x.publisher());p.setString(6,x.publicationPlace());p.setString(7,x.publicationDateText());p.setString(8,x.languageTag());p.setString(9,x.identifier());p.setString(10,x.canonicalUri());p.setString(11,x.notes());p.setTimestamp(12,Timestamp.from(x.recordedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<SourceEdition> listEditions(UUID tenant,HistoricalSourceId sourceId,int limit) {
  checkLimit(limit);return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select edition_id,source_id,edition_designation,publisher,publication_place,publication_date_text,language_tag,identifier,canonical_uri,notes,recorded_at from historical_source_edition where tenant_id=? and source_id=? order by recorded_at,edition_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<SourceEdition> out=new ArrayList<>();while(rs.next())out.add(new SourceEdition(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getTimestamp(11).toInstant()));return List.copyOf(out);}
  }});
 }
 @Override public boolean appendHolding(UUID tenant,ArchivalHolding x) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(x);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_archival_holding(tenant_id,holding_id,source_id,institution_ref,institution_name,repository_name,collection_name,shelfmark,holding_type,catalog_uri,access_conditions,notes,recorded_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,x.id());p.setObject(3,x.sourceId().value());p.setString(4,x.institutionRef());p.setString(5,x.institutionName());p.setString(6,x.repositoryName());p.setString(7,x.collectionName());p.setString(8,x.shelfmark());p.setString(9,x.holdingType().name());p.setString(10,x.catalogUri());p.setString(11,x.accessConditions());p.setString(12,x.notes());p.setTimestamp(13,Timestamp.from(x.recordedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<ArchivalHolding> listHoldings(UUID tenant,HistoricalSourceId sourceId,int limit) {
  checkLimit(limit);return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select holding_id,source_id,institution_ref,institution_name,repository_name,collection_name,shelfmark,holding_type,catalog_uri,access_conditions,notes,recorded_at from historical_archival_holding where tenant_id=? and source_id=? order by recorded_at,holding_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setInt(3,limit);try(ResultSet rs=p.executeQuery()){List<ArchivalHolding> out=new ArrayList<>();while(rs.next())out.add(new ArchivalHolding(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),ArchivalHoldingType.valueOf(rs.getString(8)),rs.getString(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12).toInstant()));return List.copyOf(out);}
  }});
 }
 @Override public Optional<ArchivalHolding> findHolding(UUID tenant,HistoricalSourceId sourceId,UUID holdingId) {
  Objects.requireNonNull(sourceId);Objects.requireNonNull(holdingId);
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select holding_id,source_id,institution_ref,institution_name,repository_name,collection_name,shelfmark,holding_type,catalog_uri,access_conditions,notes,recorded_at from historical_archival_holding where tenant_id=? and source_id=? and holding_id=?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setObject(3,holdingId);try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(new ArchivalHolding(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),ArchivalHoldingType.valueOf(rs.getString(8)),rs.getString(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12).toInstant())):Optional.empty();}
  }});
 }
 @Override public boolean appendCatalogIdentifier(UUID tenant,ArchivalCatalogIdentifier x) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(x);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_archival_catalog_identifier(tenant_id,identifier_id,holding_id,scheme,identifier_value,assigning_authority,recorded_at) values (?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,x.id());p.setObject(3,x.holdingId());p.setString(4,x.scheme());p.setString(5,x.value());p.setString(6,x.assigningAuthority());p.setTimestamp(7,Timestamp.from(x.recordedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<ArchivalCatalogIdentifier> listCatalogIdentifiers(UUID tenant,HistoricalSourceId sourceId,UUID holdingId,int limit) {
  Objects.requireNonNull(sourceId);Objects.requireNonNull(holdingId);checkLimit(limit);return read(tenant,c->{try(PreparedStatement p=c.prepareStatement("select i.identifier_id,i.holding_id,i.scheme,i.identifier_value,i.assigning_authority,i.recorded_at from historical_archival_catalog_identifier i join historical_archival_holding h on h.tenant_id=i.tenant_id and h.holding_id=i.holding_id where i.tenant_id=? and h.source_id=? and i.holding_id=? order by i.recorded_at,i.identifier_id limit ?")){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setObject(3,holdingId);p.setInt(4,limit);try(ResultSet rs=p.executeQuery()){List<ArchivalCatalogIdentifier> out=new ArrayList<>();while(rs.next())out.add(new ArchivalCatalogIdentifier(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()));return List.copyOf(out);}
  }});
 }
 @Override public boolean appendSourceRelationship(UUID tenant,SourceRelationship x) {
  Objects.requireNonNull(tenant);Objects.requireNonNull(x);
  return write(tenant,c->{try(PreparedStatement p=c.prepareStatement("insert into historical_source_relationship(tenant_id,relationship_id,source_id,related_source_id,relation,locator_type,locator,note,recorded_at) values (?,?,?,?,?,?,?,?,?)")){
   p.setObject(1,tenant);p.setObject(2,x.id());p.setObject(3,x.sourceId().value());p.setObject(4,x.relatedSourceId().value());p.setString(5,x.relation().name());p.setString(6,x.locatorType()==null?null:x.locatorType().name());p.setString(7,x.locator());p.setString(8,x.note());p.setTimestamp(9,Timestamp.from(x.recordedAt()));p.executeUpdate();return true;
  }},false);
 }
 @Override public List<SourceRelationship> listOutgoingSourceRelationships(UUID tenant,HistoricalSourceId sourceId,int limit) {
  return listSourceRelationships(tenant,sourceId,limit,true);
 }
 @Override public List<SourceRelationship> listIncomingSourceRelationships(UUID tenant,HistoricalSourceId sourceId,int limit) {
  return listSourceRelationships(tenant,sourceId,limit,false);
 }
 private List<SourceRelationship> listSourceRelationships(UUID tenant,HistoricalSourceId sourceId,int limit,boolean outgoing) {
  Objects.requireNonNull(sourceId);checkLimit(limit);
  String sql=outgoing
   ?"select relationship_id,source_id,related_source_id,relation,locator_type,locator,note,recorded_at from historical_source_relationship where tenant_id=? and source_id=? order by recorded_at,relationship_id limit ?"
   :"select relationship_id,source_id,related_source_id,relation,locator_type,locator,note,recorded_at from historical_source_relationship where tenant_id=? and related_source_id=? order by recorded_at,relationship_id limit ?";
  return read(tenant,c->{try(PreparedStatement p=c.prepareStatement(sql)){
   p.setObject(1,tenant);p.setObject(2,sourceId.value());p.setInt(3,limit);
   try(ResultSet rs=p.executeQuery()){List<SourceRelationship> out=new ArrayList<>();while(rs.next())out.add(new SourceRelationship(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),new HistoricalSourceId(rs.getObject(3,UUID.class)),SourceRelationshipType.valueOf(rs.getString(4)),rs.getString(5)==null?null:EvidenceLocatorType.valueOf(rs.getString(5)),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()));return List.copyOf(out);}
  }});
 }
 private static void checkLimit(int limit){if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");}
 private static SourceAssessment mapAssessment(ResultSet rs)throws SQLException {
  return new SourceAssessment(rs.getObject(1,UUID.class),new HistoricalSourceId(rs.getObject(2,UUID.class)),SourceAssessmentDimension.valueOf(rs.getString(3)),SourceAssessmentConclusion.valueOf(rs.getString(4)),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant());
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