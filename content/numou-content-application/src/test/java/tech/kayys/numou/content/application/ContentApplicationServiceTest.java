package tech.kayys.numou.content.application;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.spi.ContentRepository;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ContentApplicationServiceTest {
 static class InMemoryRepository implements ContentRepository {
  final Map<String,ContentEntry> entries=new HashMap<>();
  public void create(ContentEntry e){entries.put(key(e.tenantId(),e.id()),e);}
  public Optional<ContentEntry> find(TenantId t,ContentId id){return Optional.ofNullable(entries.get(key(t,id)));}
  public boolean appendRevision(ContentEntry e,int expected){String k=key(e.tenantId(),e.id());ContentEntry old=entries.get(k);if(old==null||old.currentRevisionNumber()!=expected)return false;entries.put(k,e);return true;}
  public boolean updateStatus(TenantId t,ContentId id,ContentStatus s,Instant at){String k=key(t,id);var e=entries.get(k);if(e==null)return false;entries.put(k,ContentEntry.rehydrate(e.id(),t,s,e.revisions(),e.createdAt(),at));return true;}
  private String key(TenantId t,ContentId id){return t+":"+id;}
 }
 @Test void commandsUseTrustedTenantContextAndKeepTenantsIsolated(){var repo=new InMemoryRepository();var service=new ContentApplicationService(repo,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));var tenantA=new TenantId(UUID.randomUUID());var tenantB=new TenantId(UUID.randomUUID());var actor=new ActorId(UUID.randomUUID());var created=service.create(new ContentCommandContext(tenantA,actor),"Title","title","{}");assertEquals(created.id(),service.get(new ContentCommandContext(tenantA,actor),created.id()).id());assertThrows(ContentNotFoundException.class,()->service.get(new ContentCommandContext(tenantB,actor),created.id()));}
 @Test void revisionCommandUsesExpectedVersion(){var repo=new InMemoryRepository();var service=new ContentApplicationService(repo,Clock.systemUTC());var ctx=new ContentCommandContext(new TenantId(UUID.randomUUID()),new ActorId(UUID.randomUUID()));var created=service.create(ctx,"A","a","{}");var revised=service.revise(ctx,created.id(),1,"B","b","{}");assertEquals(2,revised.currentRevisionNumber());}
}