package tech.kayys.numou.workspace.application;
import tech.kayys.numou.workspace.application.WorkspacePorts.*;
import tech.kayys.numou.workspace.domain.*;
import java.time.Clock; import java.time.Instant; import java.util.Comparator; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.UUID;
/** Application use cases; identity, authorization, storage, and file handling are injected boundaries. */
public final class WorkspaceService {
 private final WorkspaceRepository workspaces; private final SpaceRepository spaces; private final PageRepository pages;
 private final AccessPolicy access; private final AuditSink audit; private final AttachmentPolicy attachments; private final Clock clock;
 public WorkspaceService(WorkspaceRepository w,SpaceRepository s,PageRepository p,AccessPolicy a,AuditSink audit,AttachmentPolicy files,Clock clock){
  this.workspaces=Objects.requireNonNull(w);this.spaces=Objects.requireNonNull(s);this.pages=Objects.requireNonNull(p);this.access=Objects.requireNonNull(a);this.audit=Objects.requireNonNull(audit);this.attachments=Objects.requireNonNull(files);this.clock=Objects.requireNonNull(clock);
 }
 public Workspace createWorkspace(UUID tenant,UUID actor,String name,String description){
  identity(tenant,actor);access.require(tenant,actor,Resource.WORKSPACE,null,Action.CREATE);
  Workspace value=Workspace.create(tenant,name,description,clock.instant());if(!workspaces.save(value))throw new IllegalStateException("Workspace could not be created");
  audit("WORKSPACE_CREATED",tenant,actor,"WORKSPACE",value.id(),0,value.name());return value;
 }
 public Space createSpace(UUID tenant,UUID actor,UUID workspaceId,String name,String slug){
  identity(tenant,actor);workspaces.findWorkspace(tenant,workspaceId).orElseThrow(()->new IllegalArgumentException("Workspace not found for tenant"));
  access.require(tenant,actor,Resource.WORKSPACE,workspaceId,Action.UPDATE);
  if(spaces.slugExists(tenant,workspaceId,slug))throw new IllegalArgumentException("Space slug already exists in workspace");
  Space value=Space.create(tenant,workspaceId,name,slug,clock.instant());if(!spaces.save(value))throw new IllegalStateException("Space could not be created");
  audit("SPACE_CREATED",tenant,actor,"SPACE",value.id(),0,value.name());return value;
 }
 public Page createPage(UUID tenant,UUID actor,UUID workspaceId,UUID spaceId,UUID parentId,String title,List<PageBlock> blocks){
  identity(tenant,actor);workspaces.find(tenant,workspaceId).orElseThrow(()->new IllegalArgumentException("Workspace not found for tenant"));
  spaces.find(tenant,workspaceId,spaceId).orElseThrow(()->new IllegalArgumentException("Space not found for workspace"));
  access.require(tenant,actor,Resource.SPACE,spaceId,Action.CREATE);
  if(parentId!=null){Page parent=pages.findPage(tenant,parentId).orElseThrow(()->new IllegalArgumentException("Parent page not found for tenant"));if(!parent.workspaceId().equals(workspaceId)||!parent.spaceId().equals(spaceId))throw new IllegalArgumentException("Parent page must belong to the same workspace and space");}
  List<PageBlock> safe=validateFiles(tenant,actor,blocks);Instant now=clock.instant();Page page=Page.create(tenant,workspaceId,spaceId,parentId,title,safe,now);PageRevision rev=PageRevision.snapshot(page,actor,now,"Page created");
  if(!pages.saveWithRevision(page,rev,0))throw new IllegalStateException("Page and initial revision could not be committed atomically");
  audit("PAGE_CREATED",tenant,actor,"PAGE",page.id(),page.revision(),page.title());return page;
 }
 public Page updatePage(UUID tenant,UUID actor,UUID pageId,long expectedRevision,String title,List<PageBlock> blocks,String summary){
  identity(tenant,actor);if(expectedRevision<1)throw new IllegalArgumentException("expectedRevision must be positive");
  Page current=pages.find(tenant,pageId).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));access.require(tenant,actor,Resource.PAGE,pageId,Action.UPDATE);
  if(current.revision()!=expectedRevision)throw new RevisionConflictException(pageId,expectedRevision,current.revision());
  Instant now=clock.instant();Page revised=current.revise(title,validateFiles(tenant,actor,blocks),now);PageRevision rev=PageRevision.snapshot(revised,actor,now,summary);
  if(!pages.saveWithRevision(revised,rev,expectedRevision))throw new RevisionConflictException(pageId,expectedRevision,current.revision());
  audit("PAGE_UPDATED",tenant,actor,"PAGE",pageId,revised.revision(),summary);return revised;
 }
 public Page readPage(UUID tenant,UUID actor,UUID pageId){
  identity(tenant,actor);Page page=pages.find(tenant,pageId).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));access.require(tenant,actor,Resource.PAGE,pageId,Action.READ);return page;
 }
 public List<Page> listChildren(UUID tenant,UUID actor,UUID spaceId,UUID parentId){
  identity(tenant,actor);access.require(tenant,actor,Resource.SPACE,spaceId,Action.READ);
  return pages.findChildren(tenant,spaceId,parentId).stream().sorted(Comparator.comparing(Page::updatedAt).reversed()).toList();
 }
 public List<PageRevision> pageHistory(UUID tenant,UUID actor,UUID pageId){
  identity(tenant,actor);pages.find(tenant,pageId).orElseThrow(()->new IllegalArgumentException("Page not found for tenant"));access.require(tenant,actor,Resource.PAGE,pageId,Action.READ);return pages.history(tenant,pageId);
 }
 private List<PageBlock> validateFiles(UUID tenant,UUID actor,List<PageBlock> blocks){
  List<PageBlock> safe=blocks==null?List.of():List.copyOf(blocks);
  return safe.stream().map(b->{if(b.type()!=BlockType.FILE)return b;String id=attachments.requireReadableFile(tenant,actor,b.attributes().get("fileId"));return new PageBlock(b.id(),b.type(),b.text(),b.position(),Map.of("fileId",id));}).toList();
 }
 private void audit(String action,UUID tenant,UUID actor,String type,UUID id,long revision,String summary){audit.record(new AuditEntry(tenant,actor,action,type,id,revision,summary==null?"":summary,clock.instant()));}
 private static void identity(UUID tenant,UUID actor){Objects.requireNonNull(tenant,"tenantId");Objects.requireNonNull(actor,"actorId");}
 public static final class RevisionConflictException extends IllegalStateException {
  public RevisionConflictException(UUID pageId,long expected,long actual){super("Revision conflict for page "+pageId+": expected "+expected+" but current revision is "+actual);}
 }
}
