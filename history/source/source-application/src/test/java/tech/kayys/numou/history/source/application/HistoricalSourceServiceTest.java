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
 @Test void invalidLimitRejectedBeforeRepositoryAccess() {
  var service=new HistoricalSourceService(new InMemoryRepository(),UUID::randomUUID,new AllowAll(),Clock.systemUTC());
  assertThrows(IllegalArgumentException.class,()->service.list(201));
 }
 private static class AllowAll implements HistoricalSourceAuthorizationPort {
  public void requireCanRegisterSource(UUID t){} public void requireCanReadSource(UUID t,HistoricalSourceId i){}
  public void requireCanReviseSource(UUID t,HistoricalSourceId i){} public void requireCanCreateClaim(UUID t){}
  public void requireCanReadClaim(UUID t,HistoricalClaimId i){} public void requireCanReferenceSubject(UUID t,String k,String i){} public void requireCanAttachEvidence(UUID t,HistoricalClaimId c,HistoricalSourceId s){}
 }
 private static class InMemoryRepository implements HistoricalSourceRepository {
  final Map<UUID,Map<UUID,HistoricalSource>> sources=new HashMap<>(); final Map<UUID,Map<UUID,HistoricalClaim>> claims=new HashMap<>();
  final Map<UUID,List<ClaimEvidence>> evidence=new HashMap<>();
  public boolean register(UUID t,HistoricalSource s){return sources.computeIfAbsent(t,k->new HashMap<>()).putIfAbsent(s.id().value(),s)==null;}
  public Optional<HistoricalSource> find(UUID t,HistoricalSourceId i){return Optional.ofNullable(sources.getOrDefault(t,Map.of()).get(i.value()));}
  public List<HistoricalSource> list(UUID t,int n){return sources.getOrDefault(t,Map.of()).values().stream().limit(n).toList();}
  public boolean revise(UUID t,HistoricalSource s,long expected){var m=sources.get(t);if(m==null)return false;var c=m.get(s.id().value());if(c==null||c.revision()!=expected)return false;m.put(s.id().value(),s);return true;}
  public boolean createClaim(UUID t,HistoricalClaim c){return claims.computeIfAbsent(t,k->new HashMap<>()).putIfAbsent(c.id().value(),c)==null;}
  public Optional<HistoricalClaim> findClaim(UUID t,HistoricalClaimId i){return Optional.ofNullable(claims.getOrDefault(t,Map.of()).get(i.value()));}
  public boolean appendEvidence(UUID t,ClaimEvidence e){evidence.computeIfAbsent(t,k->new ArrayList<>()).add(e);return true;}
  public List<ClaimEvidence> listEvidence(UUID t,HistoricalClaimId c,int n){return evidence.getOrDefault(t,List.of()).stream().filter(e->e.claimId().equals(c)).limit(n).toList();}
 }
}