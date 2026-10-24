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
  PageLinkService service=new PageLinkService(store,store,(t,u,r,id,action)->permissions.add(id+":"+action),e->{},Clock.fixed(Instant.EPOCH,ZoneOffset.UTC),true);
  PageLink link=service.link(tenant,actor,a.id(),b.id(),"related");
  assertEquals("related",link.label());assertEquals(List.of(a),service.backlinks(tenant,actor,b.id()));assertEquals(4,permissions.size());
  assertThrows(IllegalStateException.class,()->service.link(tenant,actor,a.id(),b.id(),"duplicate"));
 }
 @Test void rejectsCrossTenantOrMissingTargetsAndSelfLinks() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Page a=page(tenant,"A");Store store=new Store(a);PageLinkService service=new PageLinkService(store,store,(t,u,r,id,action)->{},e->{},Clock.systemUTC(),true);
  assertThrows(IllegalArgumentException.class,()->service.link(tenant,actor,a.id(),UUID.randomUUID(),""));
  assertThrows(IllegalArgumentException.class,()->PageLink.create(tenant,a.id(),a.id(),actor,"",Instant.EPOCH));
 }
 @Test void unlinkRequiresTargetReadPermissionAndPreservesLinkWhenDenied() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Page source=page(tenant,"Source"),target=page(tenant,"Target");Store store=new Store(source,target);
  PageLink link=PageLink.create(tenant,source.id(),target.id(),actor,"related",Instant.EPOCH);assertTrue(store.save(link));
  PageLinkService denied=new PageLinkService(store,store,(t,a,r,id,action)->{
   if(id.equals(target.id())&&action==Action.READ)throw new SecurityException("target is not readable");
  },e->{},Clock.systemUTC(),true);
  assertThrows(SecurityException.class,()->denied.unlink(tenant,actor,link.id()));
  assertTrue(store.findLink(tenant,link.id()).isPresent(),"authorization must happen before deletion");
  assertTrue(store.findLink(UUID.randomUUID(),link.id()).isEmpty(),"link lookup must be tenant-scoped");

  PageLinkService allowed=new PageLinkService(store,store,(t,a,r,id,action)->{},e->{},Clock.systemUTC(),true);
  allowed.unlink(tenant,actor,link.id());
  assertTrue(store.findLink(tenant,link.id()).isEmpty());
 }
 @Test void outgoingLinksRequireTargetReadAndBacklinksHideUnreadableSources() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Page source=page(tenant,"Source"),target=page(tenant,"Target"),hidden=page(tenant,"Hidden source");Store store=new Store(source,target,hidden);
  PageLink visible=PageLink.create(tenant,source.id(),target.id(),actor,"visible",Instant.EPOCH);
  PageLink hiddenLink=PageLink.create(tenant,hidden.id(),target.id(),actor,"hidden",Instant.EPOCH);
  assertTrue(store.save(visible));assertTrue(store.save(hiddenLink));
  PageLinkService service=new PageLinkService(store,store,(t,a,r,id,action)->{
   if(action==Action.READ&&(id.equals(target.id())||id.equals(hidden.id())))throw new SecurityException("denied");
  },e->{},Clock.systemUTC(),true);
  assertThrows(SecurityException.class,()->service.outgoing(tenant,actor,source.id()));
  // The target page itself must be readable to ask for backlinks; test filtering with a separate policy.
  PageLinkService backlinkService=new PageLinkService(store,store,(t,a,r,id,action)->{
   if(id.equals(hidden.id())&&action==Action.READ)throw new SecurityException("hidden source");
  },e->{},Clock.systemUTC(),true);
  assertEquals(List.of(source),backlinkService.backlinks(tenant,actor,target.id()));
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
  public Optional<PageLink> findLink(UUID t,UUID id){return Optional.ofNullable(links.get(id)).filter(l->l.tenantId().equals(t));}
  public boolean delete(UUID t,UUID id){PageLink link=links.get(id);if(link==null||!link.tenantId().equals(t))return false;links.remove(id);return true;}
  public List<PageLink> outgoing(UUID t,UUID id){return links.values().stream().filter(l->l.tenantId().equals(t)&&l.sourcePageId().equals(id)).toList();}
  public List<PageLink> backlinks(UUID t,UUID id){return links.values().stream().filter(l->l.tenantId().equals(t)&&l.targetPageId().equals(id)).toList();}
  public void require(UUID t,UUID a,Resource r,UUID id,Action action){}
 }
}
