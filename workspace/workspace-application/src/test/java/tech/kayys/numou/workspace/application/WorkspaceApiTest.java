package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspaceApi.*;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;
import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceApiTest {
 @Test void trustedIdentityAndPageCreationWorkflow(){UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Fixture f=new Fixture();WorkspaceApi api=new WorkspaceApi(f.service(),()->new AuthenticatedActor(tenant,actor));WorkspaceView w=api.createWorkspace(new CreateWorkspaceRequest("Team","Docs"));SpaceView s=api.createSpace(w.id(),new CreateSpaceRequest("General","general"));PageView p=api.createPage(w.id(),s.id(),new CreatePageRequest(null,"Home",List.of(new BlockRequest(null,"paragraph","Welcome",0,Map.of()))));assertEquals(tenant,f.lastTenant);assertEquals(actor,f.lastActor);assertEquals(1,p.revision());assertEquals("Welcome",p.blocks().get(0).text());assertEquals(1,api.getPageHistory(p.id()).size());}

 @Test void navigationIsTenantScopedAndDeterministicallyOrdered(){
  UUID tenantA=UUID.randomUUID(),tenantB=UUID.randomUUID(),actor=UUID.randomUUID();Fixture f=new Fixture();
  WorkspaceApi apiA=new WorkspaceApi(f.service(),()->new AuthenticatedActor(tenantA,actor));
  WorkspaceApi apiB=new WorkspaceApi(f.service(),()->new AuthenticatedActor(tenantB,actor));
  WorkspaceView first=apiA.createWorkspace(new CreateWorkspaceRequest("First",""));
  apiB.createWorkspace(new CreateWorkspaceRequest("Other tenant",""));
  WorkspaceView second=apiA.createWorkspace(new CreateWorkspaceRequest("Second",""));
  assertEquals(List.of(first.id(),second.id()),apiA.listWorkspaces().stream().map(WorkspaceView::id).toList());
  SpaceView spaceA=apiA.createSpace(first.id(),new CreateSpaceRequest("General","general"));
  apiA.createSpace(second.id(),new CreateSpaceRequest("Operations","operations"));
  assertEquals(List.of(spaceA.id()),apiA.listSpaces(first.id()).stream().map(SpaceView::id).toList());
  ApiException foreign=assertThrows(ApiException.class,()->apiB.listSpaces(first.id()));
  assertEquals(400,foreign.status());
 }
 @Test void navigationRequiresWorkspaceReadAuthorization(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Fixture f=new Fixture();
  WorkspaceApi api=new WorkspaceApi(f.service(),()->new AuthenticatedActor(tenant,actor));
  WorkspaceView workspace=api.createWorkspace(new CreateWorkspaceRequest("Private",""));
  f.service().createSpace(tenant,actor,workspace.id(),"General","general");
  f.denyWorkspaceRead=true;
  assertTrue(api.listWorkspaces().isEmpty());
  ApiException denied=assertThrows(ApiException.class,()->api.listSpaces(workspace.id()));
  assertEquals(403,denied.status());
 }
 @Test void rejectsMissingAuthenticatedIdentity(){WorkspaceApi api=new WorkspaceApi(new Fixture().service(),()->null);ApiException e=assertThrows(ApiException.class,()->api.createWorkspace(new CreateWorkspaceRequest("x","")));assertEquals(401,e.status());}
 @Test void staleRevisionMapsToConflict(){UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID();Fixture f=new Fixture();WorkspaceApi api=new WorkspaceApi(f.service(),()->new AuthenticatedActor(tenant,actor));WorkspaceView w=api.createWorkspace(new CreateWorkspaceRequest("Team",""));SpaceView s=api.createSpace(w.id(),new CreateSpaceRequest("General","general"));PageView p=api.createPage(w.id(),s.id(),new CreatePageRequest(null,"Home",List.of()));api.updatePage(p.id(),new UpdatePageRequest(1,"Next",List.of(),"first"));ApiException e=assertThrows(ApiException.class,()->api.updatePage(p.id(),new UpdatePageRequest(1,"Stale",List.of(),"stale")));assertEquals(409,e.status());}
 static final class Fixture{
  UUID lastTenant,lastActor;boolean denyWorkspaceRead;Map<UUID,Workspace> ws=new HashMap<>();Map<UUID,Space> ss=new HashMap<>();Map<UUID,Page> ps=new HashMap<>();Map<UUID,List<PageRevision>> hs=new HashMap<>();
  WorkspaceService service(){return new WorkspaceService(new WorkspaceRepository(){public boolean save(Workspace w){return ws.putIfAbsent(w.id(),w)==null;}public Optional<Workspace> findWorkspace(UUID t,UUID id){return Optional.ofNullable(ws.get(id)).filter(x->x.tenantId().equals(t));}public List<Workspace> findByTenant(UUID t){return ws.values().stream().filter(x->x.tenantId().equals(t)).toList();}},new SpaceRepository(){public boolean save(Space s){return ss.putIfAbsent(s.id(),s)==null;}public Optional<Space> find(UUID t,UUID w,UUID id){return Optional.ofNullable(ss.get(id)).filter(x->x.tenantId().equals(t)&&x.workspaceId().equals(w));}public boolean slugExists(UUID t,UUID w,String slug){return ss.values().stream().anyMatch(x->x.tenantId().equals(t)&&x.workspaceId().equals(w)&&x.slug().equalsIgnoreCase(slug));}public List<Space> findByWorkspace(UUID t,UUID w){return ss.values().stream().filter(x->x.tenantId().equals(t)&&x.workspaceId().equals(w)).toList();}},new PageRepository(){public Optional<Page> findPage(UUID t,UUID id){return Optional.ofNullable(ps.get(id)).filter(x->x.tenantId().equals(t));}public List<Page> findChildren(UUID t,UUID s,UUID parent){return ps.values().stream().filter(x->x.tenantId().equals(t)&&x.spaceId().equals(s)&&Objects.equals(x.parentPageId(),parent)).toList();}public boolean saveWithRevision(Page p,PageRevision r,long expected){Page old=ps.get(p.id());if(expected==0?old!=null:old==null||old.revision()!=expected)return false;ps.put(p.id(),p);hs.computeIfAbsent(p.id(),k->new ArrayList<>()).add(r);return true;}public List<PageRevision> history(UUID t,UUID id){return findPage(t,id).map(x->List.copyOf(hs.getOrDefault(id,List.of()))).orElse(List.of());}},(t,a,r,id,action)->{lastTenant=t;lastActor=a;},e->{},(t,a,id)->id,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),true);}
 }
}
