package tech.kayys.numou.workspace.application;
import org.junit.jupiter.api.Test; import tech.kayys.numou.workspace.application.WorkspacePorts.*; import tech.kayys.numou.workspace.domain.*;
import java.time.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*; import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
class WorkspaceServiceTest {
 @Test void auditedWritesFailClosedWhenAdapterLacksAtomicCapability(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();Fake f=new Fake();WorkspaceService service=f.service(false);
  f.workspaces.put(wid, new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid, new Space(sid,tenant,wid,"S","s",Instant.EPOCH));
  assertThrows(IllegalStateException.class,()->service.createPage(tenant,actor,wid,sid,null,"Home",List.of()));
  assertTrue(f.current.isEmpty(),"fail-closed path must reject before writing the page");
  assertTrue(f.history.isEmpty(),"fail-closed path must reject before writing revision history");
 }
 @Test void createPagePersistsInitialRevisionAndRequiresSpacePermission(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();Fake f=new Fake();WorkspaceService service=f.service();
  f.workspaces.put(wid, new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid, new Space(sid,tenant,wid,"S","s",Instant.EPOCH));
  Page p=service.createPage(tenant,actor,wid,sid,null,"Home",List.of(PageBlock.text(BlockType.PARAGRAPH,"Hello",0)));
  assertEquals(1,p.revision());assertEquals(1,f.history.get(p.id()).size());assertTrue(f.actions.contains(Resource.SPACE+":"+sid+":"+Action.CREATE));
 }
 @Test void updateUsesOptimisticRevisionAndWritesHistory(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();Fake f=new Fake();WorkspaceService service=f.service();
  f.workspaces.put(wid, new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid, new Space(sid,tenant,wid,"S","s",Instant.EPOCH));
  Page p=service.createPage(tenant,actor,wid,sid,null,"Home",List.of());Page next=service.updatePage(tenant,actor,p.id(),1,"Renamed",List.of(),"rename");
  assertEquals(2,next.revision());assertEquals(2,f.history.get(p.id()).size());assertThrows(WorkspaceService.RevisionConflictException.class,()->service.updatePage(tenant,actor,p.id(),1,"stale",List.of(),"stale"));
 }
 @Test void fileReferencesMustPassAttachmentPolicy(){
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();Fake f=new Fake();WorkspaceService service=f.service();
  f.workspaces.put(wid, new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid, new Space(sid,tenant,wid,"S","s",Instant.EPOCH));f.rejectFiles=true;
  PageBlock file=new PageBlock(UUID.randomUUID(),BlockType.FILE,"",0,Map.of("fileId","foreign-file"));
  assertThrows(SecurityException.class,()->service.createPage(tenant,actor,wid,sid,null,"File page",List.of(file)));
 }
 @Test void publishesSignalsForCommittedCreateUpdateAndRestoreOnly() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();
  Fake f=new Fake();List<WorkspacePageChangeSignal> signals=new ArrayList<>();WorkspaceService service=f.service(true,signals::add);
  f.workspaces.put(wid,new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid,new Space(sid,tenant,wid,"S","s",Instant.EPOCH));
  Page created=service.createPage(tenant,actor,wid,sid,null,"Home",List.of());
  assertEquals(1,signals.size());assertEquals(WorkspacePageChangeSignal.ChangeType.CREATED,signals.get(0).changeType());
  Page updated=service.updatePage(tenant,actor,created.id(),1,"Renamed",List.of(),"rename");
  assertEquals(2,signals.size());assertEquals(WorkspacePageChangeSignal.ChangeType.UPDATED,signals.get(1).changeType());
  Page restored=service.restorePageRevision(tenant,actor,created.id(),1,2);
  assertEquals(3,restored.revision());assertEquals(3,signals.size());
  assertEquals(WorkspacePageChangeSignal.ChangeType.RESTORED,signals.get(2).changeType());
  assertThrows(WorkspaceService.RevisionConflictException.class,()->service.updatePage(tenant,actor,created.id(),2,"stale",List.of(),"stale"));
  assertEquals(3,signals.size(),"failed writes must not publish signals");
 }
 @Test void notifierFailureDoesNotTurnCommittedPageWriteIntoCommandFailure() {
  UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),wid=UUID.randomUUID(),sid=UUID.randomUUID();Fake f=new Fake();
  WorkspaceService service=f.service(true,signal->{throw new IllegalStateException("gateway unavailable");});
  f.workspaces.put(wid,new Workspace(wid,tenant,"W","",Instant.EPOCH));f.spaces.put(sid,new Space(sid,tenant,wid,"S","s",Instant.EPOCH));
  Page created=assertDoesNotThrow(()->service.createPage(tenant,actor,wid,sid,null,"Home",List.of()));
  assertEquals(1,created.revision());assertEquals(1,f.history.get(created.id()).size());
 }
 static final class Fake {
  Map<UUID,Workspace> workspaces=new HashMap<>();Map<UUID,Space> spaces=new HashMap<>();Map<UUID,Page> current=new HashMap<>();Map<UUID,List<PageRevision>> history=new HashMap<>();List<String> actions=new ArrayList<>();boolean rejectFiles;
  WorkspaceService service(){return service(true);}
  WorkspaceService service(boolean allowNonAtomicAuditFallback){return service(allowNonAtomicAuditFallback,signal -> {});}
  WorkspaceService service(boolean allowNonAtomicAuditFallback,tech.kayys.numou.workspace.application.port.WorkspacePageChangeNotifier notifier){return new WorkspaceService(new WorkspaceRepository(){public boolean save(Workspace w){return workspaces.putIfAbsent(w.id(),w)==null;}public Optional<Workspace> findWorkspace(UUID t,UUID id){return Optional.ofNullable(workspaces.get(id)).filter(w->w.tenantId().equals(t));}},
   new SpaceRepository(){public boolean save(Space s){return spaces.putIfAbsent(s.id(),s)==null;}public Optional<Space> find(UUID t,UUID w,UUID id){return Optional.ofNullable(spaces.get(id)).filter(s->s.tenantId().equals(t)&&s.workspaceId().equals(w));}public boolean slugExists(UUID t,UUID w,String slug){return spaces.values().stream().anyMatch(s->s.tenantId().equals(t)&&s.workspaceId().equals(w)&&s.slug().equalsIgnoreCase(slug));}},
   new PageRepository(){public Optional<Page> findPage(UUID t,UUID id){return Optional.ofNullable(current.get(id)).filter(p->p.tenantId().equals(t));}public List<Page> findChildren(UUID t,UUID s,UUID parent){return current.values().stream().filter(p->p.tenantId().equals(t)&&p.spaceId().equals(s)&&Objects.equals(p.parentPageId(),parent)).toList();}
    public boolean saveWithRevision(Page p,PageRevision r,long expected){Page old=current.get(p.id());if(expected==0){if(old!=null)return false;}else if(old==null||old.revision()!=expected)return false;current.put(p.id(),p);history.computeIfAbsent(p.id(),k->new ArrayList<>()).add(r);return true;}
    public List<PageRevision> history(UUID t,UUID id){return findPage(t,id).map(p->List.copyOf(history.getOrDefault(id,List.of()))).orElse(List.of());}},
   (t,a,r,id,act)->actions.add(r+":"+id+":"+act),e->{},(t,a,id)->{if(rejectFiles)throw new SecurityException("file not accessible");return id;},Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),allowNonAtomicAuditFallback,notifier);}
 }
}
