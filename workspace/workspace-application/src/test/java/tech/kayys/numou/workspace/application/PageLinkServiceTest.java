package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageLinkServiceTest {
 @Test void linkCreatesBacklinkAndRequiresPermissions() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Page a=page(tenant,"A"),b=page(tenant,"B");Store store=new Store(a,b);List<String> permissions=new ArrayList<>();
  PageLinkService service=new PageLinkService(store,store,(t,u,r,id,action)->permissions.add(id+":"+action),e->{},Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
  PageLink link=service.link(tenant,actor,a.id(),b.id(),"related");
  assertEquals("related",link.label());assertEquals(List.of(a),service.backlinks(tenant,actor,b.id()));assertEquals(2,permissions.size());
  assertThrows(IllegalStateException.class,()->service.link(tenant,actor,a.id(),b.id(),"duplicate"));
 }
 @Test void rejectsCrossTenantOrMissingTargetsAndSelfLinks() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Page a=page(tenant,"A");Store store=new Store(a);PageLinkService service=new PageLinkService(store,store,(t,u,r,id,action)->{},e->{},Clock.systemUTC());
  assertThrows(IllegalArgumentException.class,()->service.link(tenant,actor,a.id(),UUID.randomUUID(),""));
  assertThrows(IllegalArgumentException.class,()->PageLink.create(tenant,a.id(),a.id(),actor,"",Instant.EPOCH));
 }
 static Page page(UUID tenant,String title){UUID w=UUID.randomUUID(),s=UUID.randomUUID();return new Page(UUID.randomUUID(),tenant,w,s,null,title,List.of(),1,Instant.EPOCH,Instant.EPOCH);}
 static final class Store implements PageRepository,PageLinkRepository,AccessPolicy {
  final Map<UUID,Page> pages=new HashMap<>();final Map<UUID,PageLink> links=new LinkedHashMap<>();
  Store(Page... values){for(Page p:values)pages.put(p.id(),p);}
  public Optional<Page> findPage(UUID t,UUID id){return Optional.ofNullable(pages.get(id)).filter(p->p.tenantId().equals(t));}
  public List<Page> findChildren(UUID t,UUID s,UUID p){return List.of();}
  public boolean saveWithRevision(Page p,PageRevision r,long e){return false;}
  public List<PageRevision> history(UUID t,UUID p){return List.of();}
  public boolean save(PageLink l){if(links.values().stream().anyMatch(x->x.tenantId().equals(l.tenantId())&&x.sourcePageId().equals(l.sourcePageId())&&x.targetPageId().equals(l.targetPageId())))return false;links.put(l.id(),l);return true;}
  public Optional<PageLink> find(UUID t,UUID id){return Optional.ofNullable(links.get(id)).filter(l->l.tenantId().equals(t));}
  public boolean delete(UUID t,UUID id){return links.remove(id)!=null;}
  public List<PageLink> outgoing(UUID t,UUID id){return links.values().stream().filter(l->l.tenantId().equals(t)&&l.sourcePageId().equals(id)).toList();}
  public List<PageLink> backlinks(UUID t,UUID id){return links.values().stream().filter(l->l.tenantId().equals(t)&&l.targetPageId().equals(id)).toList();}
  public void require(UUID t,UUID a,Resource r,UUID id,Action action){}
 }
}
