package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Tenant-authorized source catalog, claim registry and evidence association use cases. */
public final class HistoricalSourceService {
 private final HistoricalSourceRepository repository;
 private final HistoricalSourceTenantContext tenantContext;
 private final HistoricalSourceAuthorizationPort authorization;
 private final Clock clock;
 public HistoricalSourceService(HistoricalSourceRepository repository, HistoricalSourceTenantContext tenantContext,
   HistoricalSourceAuthorizationPort authorization, Clock clock) {
  this.repository=Objects.requireNonNull(repository); this.tenantContext=Objects.requireNonNull(tenantContext);
  this.authorization=Objects.requireNonNull(authorization); this.clock=Objects.requireNonNull(clock);
 }
 public HistoricalSource register(HistoricalSourceKind kind,String title,String subtitle,String description,
   String canonicalUri,String publisher,String publicationDateText,String languageTag) {
  var tenant=requireTenant(); authorization.requireCanRegisterSource(tenant);
  var source=HistoricalSource.register(HistoricalSourceId.generate(),kind,title,subtitle,description,canonicalUri,
    publisher,publicationDateText,languageTag,clock.instant());
  if(!repository.register(tenant,source)) throw new HistoricalSourceConflictException("Source identity already exists");
  return source;
 }
 public Optional<HistoricalSource> find(HistoricalSourceId id) {
  var tenant=requireTenant(); Objects.requireNonNull(id); authorization.requireCanReadSource(tenant,id);
  return repository.find(tenant,id);
 }
 public List<HistoricalSource> list(int limit) {
  var tenant=requireTenant(); validateLimit(limit);
  var sources=repository.list(tenant,limit);
  sources.forEach(source->authorization.requireCanReadSource(tenant,source.id()));
  return List.copyOf(sources);
 }
 public HistoricalSource revise(HistoricalSourceId id,long expectedRevision,HistoricalSourceKind kind,String title,
   String subtitle,String description,String canonicalUri,String publisher,String publicationDateText,String languageTag) {
  var tenant=requireTenant(); Objects.requireNonNull(id); authorization.requireCanReviseSource(tenant,id);
  var current=repository.find(tenant,id).orElseThrow(()->new IllegalArgumentException("Historical source not found"));
  if(current.revision()!=expectedRevision) throw new HistoricalSourceConflictException("Source revision changed; reload before retrying");
  var revised=current.revise(kind,title,subtitle,description,canonicalUri,publisher,publicationDateText,languageTag,clock.instant());
  if(!repository.revise(tenant,revised,expectedRevision)) throw new HistoricalSourceConflictException("Source revision changed concurrently");
  return revised;
 }
 public HistoricalSource archive(HistoricalSourceId id,long expectedRevision) {
  var tenant=requireTenant(); Objects.requireNonNull(id); authorization.requireCanReviseSource(tenant,id);
  var current=repository.find(tenant,id).orElseThrow(()->new IllegalArgumentException("Historical source not found"));
  if(current.revision()!=expectedRevision) throw new HistoricalSourceConflictException("Source revision changed; reload before retrying");
  var archived=current.archive(clock.instant());
  if(archived==current) return current;
  if(!repository.revise(tenant,archived,expectedRevision)) throw new HistoricalSourceConflictException("Source revision changed concurrently");
  return archived;
 }
 public HistoricalClaim createClaim(String subjectKind,String subjectId,String statement) {
  var tenant=requireTenant(); authorization.requireCanCreateClaim(tenant);
  authorization.requireCanReferenceSubject(tenant,subjectKind,subjectId);
  var claim=HistoricalClaim.create(HistoricalClaimId.generate(),subjectKind,subjectId,statement,clock.instant());
  if(!repository.createClaim(tenant,claim)) throw new HistoricalSourceConflictException("Claim identity already exists");
  return claim;
 }
 public Optional<HistoricalClaim> findClaim(HistoricalClaimId id) {
  var tenant=requireTenant(); Objects.requireNonNull(id); authorization.requireCanReadClaim(tenant,id);
  return repository.findClaim(tenant,id);
 }
 public ClaimEvidence attachEvidence(HistoricalClaimId claimId,HistoricalSourceId sourceId,EvidenceRelation relation,
   EvidenceLocatorType locatorType,String locator,String excerpt) {
  var tenant=requireTenant(); Objects.requireNonNull(claimId); Objects.requireNonNull(sourceId);
  authorization.requireCanAttachEvidence(tenant,claimId,sourceId);
  if(repository.findClaim(tenant,claimId).isEmpty()) throw new IllegalArgumentException("Historical claim not found");
  if(repository.find(tenant,sourceId).isEmpty()) throw new IllegalArgumentException("Historical source not found");
  var evidence=ClaimEvidence.create(claimId,sourceId,relation,locatorType,locator,excerpt,clock.instant());
  if(!repository.appendEvidence(tenant,evidence)) throw new HistoricalSourceConflictException("Evidence could not be appended");
  return evidence;
 }
 public List<ClaimEvidence> listEvidence(HistoricalClaimId claimId,int limit) {
  var tenant=requireTenant(); Objects.requireNonNull(claimId); validateLimit(limit);
  authorization.requireCanReadClaim(tenant,claimId);
  if(repository.findClaim(tenant,claimId).isEmpty()) return List.of();
  var items=repository.listEvidence(tenant,claimId,limit);
  items.forEach(item->authorization.requireCanReadSource(tenant,item.sourceId()));
  return List.copyOf(items);
 }
 public SourceAssessment assessSource(HistoricalSourceId sourceId, SourceAssessmentDimension dimension,
   SourceAssessmentConclusion conclusion, String rationale, String methodologyId, String methodologyVersion, String assessorRef) {
  var tenant=requireTenant(); Objects.requireNonNull(sourceId);
  authorization.requireCanAssessSource(tenant,sourceId,assessorRef);
  if(repository.find(tenant,sourceId).isEmpty()) throw new IllegalArgumentException("Historical source not found");
  var assessment=SourceAssessment.create(sourceId,dimension,conclusion,rationale,methodologyId,methodologyVersion,assessorRef,clock.instant());
  if(!repository.appendAssessment(tenant,assessment)) throw new HistoricalSourceConflictException("Assessment could not be appended");
  return assessment;
 }
 public List<SourceAssessment> listAssessments(HistoricalSourceId sourceId,int limit) {
  var tenant=requireTenant(); Objects.requireNonNull(sourceId); validateLimit(limit);
  authorization.requireCanReadSource(tenant,sourceId);
  if(repository.find(tenant,sourceId).isEmpty()) return List.of();
  return List.copyOf(repository.listAssessments(tenant,sourceId,limit));
 }
 public SourceAssessmentReview reviewAssessment(HistoricalSourceId sourceId, UUID assessmentId,
   SourceAssessmentReviewDecision decision,String rationale,String reviewerRef) {
  var tenant=requireTenant(); Objects.requireNonNull(sourceId); Objects.requireNonNull(assessmentId);
  authorization.requireCanReviewSourceAssessment(tenant,sourceId,assessmentId,reviewerRef);
  var assessment=repository.findAssessment(tenant,assessmentId).orElseThrow(()->new IllegalArgumentException("Source assessment not found"));
  if(!assessment.sourceId().equals(sourceId)) throw new IllegalArgumentException("Assessment does not belong to source");
  var review=SourceAssessmentReview.create(assessmentId,decision,rationale,reviewerRef,clock.instant());
  if(!repository.appendAssessmentReview(tenant,review)) throw new HistoricalSourceConflictException("Review could not be appended");
  return review;
 }
 public List<SourceAssessmentReview> listAssessmentReviews(HistoricalSourceId sourceId,UUID assessmentId,int limit) {
  var tenant=requireTenant(); Objects.requireNonNull(sourceId); Objects.requireNonNull(assessmentId); validateLimit(limit);
  authorization.requireCanReadSource(tenant,sourceId);
  var assessment=repository.findAssessment(tenant,assessmentId);
  if(assessment.isEmpty() || !assessment.get().sourceId().equals(sourceId)) return List.of();
  return List.copyOf(repository.listAssessmentReviews(tenant,assessmentId,limit));
 }
 private java.util.UUID requireTenant() { return Objects.requireNonNull(tenantContext.requireTenantId(),"trusted tenant context is required"); }
 private static void validateLimit(int limit) { if(limit<1||limit>200) throw new IllegalArgumentException("limit must be between 1 and 200"); }
}