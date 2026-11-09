package tech.kayys.numou.history.source.application;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HistoricalSourceServiceTest {
 @Test void tenantContextAndAuthorizationAreRequiredBeforeWrites() {
  var repo=new InMemoryRepository(); var tenant=UUID.randomUUID(); var auth=new AllowAll();
  var service=new HistoricalSourceService(repo,()->tenant,auth,Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"),ZoneOffset.UTC));
  var source=service.register(HistoricalSourceKind.BOOK,"Chronicle",null,null,null,null,"c. 1200 CE","id");
  var claim=service.createClaim("EVENT","event-1","A settlement was founded.");
  var evidence=service.attachEvidence(claim.id(),source.id(),EvidenceRelation.SUPPORTS,EvidenceLocatorType.PAGE,"p. 12","Founding account");
  assertEquals(source.id(),evidence.sourceId()); assertEquals(1,service.listEvidence(claim.id(),10).size());
 }
 @Test void assessmentsRequireMethodologyAndKeepEditorialReviewAsSeparateHistory() {
  var repo=new InMemoryRepository(); var tenant=UUID.randomUUID(); var service=new HistoricalSourceService(repo,()->tenant,new AllowAll(),Clock.systemUTC());
  var source=service.register(HistoricalSourceKind.MANUSCRIPT,"Chronicle",null,null,null,null,null,"id");
  var assessment=service.assessSource(source.id(),SourceAssessmentDimension.INDEPENDENCE,
   SourceAssessmentConclusion.UNCERTAIN,"Two accounts derive from the same earlier text.","source-criticism","1.0","actor:researcher");
  var review=service.reviewAssessment(source.id(),assessment.id(),SourceAssessmentReviewDecision.REQUEST_CHANGES,
   "Document the textual dependency.","actor:editor");
  assertEquals(assessment.id(),review.assessmentId());
  assertEquals(1,service.listAssessments(source.id(),10).size());
  assertEquals(1,service.listAssessmentReviews(source.id(),assessment.id(),10).size());
 }
 @Test void invalidLimitRejectedBeforeRepositoryAccess() {
  var service=new HistoricalSourceService(new InMemoryRepository(),UUID::randomUUID,new AllowAll(),Clock.systemUTC());
  assertThrows(IllegalArgumentException.class,()->service.list(201));
 }
 private static class AllowAll implements HistoricalSourceAuthorizationPort {
  public void requireCanRegisterSource(UUID t){} public void requireCanReadSource(UUID t,HistoricalSourceId i){}
  public void requireCanReviseSource(UUID t,HistoricalSourceId i){} public void requireCanCreateClaim(UUID t){}
  public void requireCanReadClaim(UUID t,HistoricalClaimId i){} public void requireCanReferenceSubject(UUID t,String k,String i){} public void requireCanAttachEvidence(UUID t,HistoricalClaimId c,HistoricalSourceId s){} public void requireCanAssessSource(UUID t,HistoricalSourceId s,String a){} public void requireCanReviewSourceAssessment(UUID t,HistoricalSourceId s,UUID a,String r){} public void requireCanManageSourceBibliography(UUID t,HistoricalSourceId s){} public void requireCanReadSourceBibliography(UUID t,HistoricalSourceId s){}
 }
 private static class InMemoryRepository implements HistoricalSourceRepository {
  final Map<UUID,Map<UUID,HistoricalSource>> sources=new HashMap<>(); final Map<UUID,Map<UUID,HistoricalClaim>> claims=new HashMap<>();
  final Map<UUID,List<ClaimEvidence>> evidence=new HashMap<>(); final Map<UUID,List<SourceAssessment>> assessments=new HashMap<>(); final Map<UUID,List<SourceAssessmentReview>> reviews=new HashMap<>(); final Map<UUID,List<SourceContributor>> contributors=new HashMap<>(); final Map<UUID,List<SourceEdition>> editions=new HashMap<>(); final Map<UUID,List<ArchivalHolding>> holdings=new HashMap<>(); final Map<UUID,List<ArchivalCatalogIdentifier>> identifiers=new HashMap<>();
  public boolean register(UUID t,HistoricalSource s){return sources.computeIfAbsent(t,k->new HashMap<>()).putIfAbsent(s.id().value(),s)==null;}
  public Optional<HistoricalSource> find(UUID t,HistoricalSourceId i){return Optional.ofNullable(sources.getOrDefault(t,Map.of()).get(i.value()));}
  public List<HistoricalSource> list(UUID t,int n){return sources.getOrDefault(t,Map.of()).values().stream().limit(n).toList();}
  public boolean revise(UUID t,HistoricalSource s,long expected){var m=sources.get(t);if(m==null)return false;var c=m.get(s.id().value());if(c==null||c.revision()!=expected)return false;m.put(s.id().value(),s);return true;}
  public boolean createClaim(UUID t,HistoricalClaim c){return claims.computeIfAbsent(t,k->new HashMap<>()).putIfAbsent(c.id().value(),c)==null;}
  public Optional<HistoricalClaim> findClaim(UUID t,HistoricalClaimId i){return Optional.ofNullable(claims.getOrDefault(t,Map.of()).get(i.value()));}
  public boolean appendEvidence(UUID t,ClaimEvidence e){evidence.computeIfAbsent(t,k->new ArrayList<>()).add(e);return true;}
  public List<ClaimEvidence> listEvidence(UUID t,HistoricalClaimId c,int n){return evidence.getOrDefault(t,List.of()).stream().filter(e->e.claimId().equals(c)).limit(n).toList();}
  public boolean appendAssessment(UUID t,SourceAssessment a){assessments.computeIfAbsent(t,k->new ArrayList<>()).add(a);return true;}
  public List<SourceAssessment> listAssessments(UUID t,HistoricalSourceId s,int n){return assessments.getOrDefault(t,List.of()).stream().filter(a->a.sourceId().equals(s)).limit(n).toList();}
  public Optional<SourceAssessment> findAssessment(UUID t,UUID id){return assessments.getOrDefault(t,List.of()).stream().filter(a->a.id().equals(id)).findFirst();}
  public boolean appendAssessmentReview(UUID t,SourceAssessmentReview r){reviews.computeIfAbsent(t,k->new ArrayList<>()).add(r);return true;}
  public List<SourceAssessmentReview> listAssessmentReviews(UUID t,UUID id,int n){return reviews.getOrDefault(t,List.of()).stream().filter(r->r.assessmentId().equals(id)).limit(n).toList();}
  public boolean appendContributor(UUID t,SourceContributor x){contributors.computeIfAbsent(t,k->new ArrayList<>()).add(x);return true;}
  public List<SourceContributor> listContributors(UUID t,HistoricalSourceId s,int n){return contributors.getOrDefault(t,List.of()).stream().filter(x->x.sourceId().equals(s)).limit(n).toList();}
  public boolean appendEdition(UUID t,SourceEdition x){editions.computeIfAbsent(t,k->new ArrayList<>()).add(x);return true;}
  public List<SourceEdition> listEditions(UUID t,HistoricalSourceId s,int n){return editions.getOrDefault(t,List.of()).stream().filter(x->x.sourceId().equals(s)).limit(n).toList();}
  public boolean appendHolding(UUID t,ArchivalHolding x){holdings.computeIfAbsent(t,k->new ArrayList<>()).add(x);return true;}
  public List<ArchivalHolding> listHoldings(UUID t,HistoricalSourceId s,int n){return holdings.getOrDefault(t,List.of()).stream().filter(x->x.sourceId().equals(s)).limit(n).toList();}
  public boolean appendCatalogIdentifier(UUID t,ArchivalCatalogIdentifier x){identifiers.computeIfAbsent(t,k->new ArrayList<>()).add(x);return true;}
  public List<ArchivalCatalogIdentifier> listCatalogIdentifiers(UUID t,HistoricalSourceId s,UUID h,int n){return identifiers.getOrDefault(t,List.of()).stream().filter(x->x.holdingId().equals(h)).filter(x->holdings.getOrDefault(t,List.of()).stream().anyMatch(v->v.id().equals(h)&&v.sourceId().equals(s))).limit(n).toList();}
 }
}