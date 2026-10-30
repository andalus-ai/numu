package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Transport-neutral API boundary. The trusted host supplies identity; request DTOs cannot select tenant or actor IDs. */
public final class WorkspaceApi {
 private final WorkspaceService service; private final IdentityProvider identity;
 public WorkspaceApi(WorkspaceService service, IdentityProvider identity){this.service=Objects.requireNonNull(service);this.identity=Objects.requireNonNull(identity);}
 public WorkspaceView createWorkspace(CreateWorkspaceRequest request){Objects.requireNonNull(request);var a=currentActor();return WorkspaceView.from(call(()->service.createWorkspace(a.tenantId(),a.actorId(),request.name(),request.description())));}
 public List<WorkspaceView> listWorkspaces(){var a=currentActor();return call(()->service.listWorkspaces(a.tenantId(),a.actorId()).stream().map(WorkspaceView::from).toList());}
 public List<SpaceView> listSpaces(UUID workspaceId){Objects.requireNonNull(workspaceId);var a=currentActor();return call(()->service.listSpaces(a.tenantId(),a.actorId(),workspaceId).stream().map(SpaceView::from).toList());}
 public SpaceView createSpace(UUID workspaceId,CreateSpaceRequest request){Objects.requireNonNull(workspaceId);Objects.requireNonNull(request);var a=currentActor();return SpaceView.from(call(()->service.createSpace(a.tenantId(),a.actorId(),workspaceId,request.name(),request.slug())));}
 public PageView createPage(UUID workspaceId,UUID spaceId,CreatePageRequest request){Objects.requireNonNull(workspaceId);Objects.requireNonNull(spaceId);Objects.requireNonNull(request);var a=currentActor();List<PageBlock> blocks=blocks(request.blocks());return PageView.from(call(()->service.createPage(a.tenantId(),a.actorId(),workspaceId,spaceId,request.parentPageId(),request.title(),blocks)));}
 public PageView getPage(UUID pageId){Objects.requireNonNull(pageId);var a=currentActor();return PageView.from(call(()->service.readPage(a.tenantId(),a.actorId(),pageId)));}
 public List<PageView> listChildren(UUID spaceId,UUID parentPageId){Objects.requireNonNull(spaceId);var a=currentActor();return call(()->service.listChildren(a.tenantId(),a.actorId(),spaceId,parentPageId).stream().map(PageView::from).toList());}
 public PageView updatePage(UUID pageId,UpdatePageRequest request){Objects.requireNonNull(pageId);Objects.requireNonNull(request);var a=currentActor();List<PageBlock> blocks=blocks(request.blocks());return PageView.from(call(()->service.updatePage(a.tenantId(),a.actorId(),pageId,request.expectedRevision(),request.title(),blocks,request.changeSummary())));}
 public List<RevisionView> getPageHistory(UUID pageId){Objects.requireNonNull(pageId);var a=currentActor();return call(()->service.pageHistory(a.tenantId(),a.actorId(),pageId).stream().map(RevisionView::from).toList());}
 public PageView restorePageRevision(UUID pageId,RestorePageRevisionRequest request){Objects.requireNonNull(pageId);Objects.requireNonNull(request);var a=currentActor();return PageView.from(call(()->service.restorePageRevision(a.tenantId(),a.actorId(),pageId,request.sourceRevision(),request.expectedCurrentRevision())));}
 private static List<PageBlock> blocks(List<BlockRequest> requests){return requests==null?List.of():requests.stream().map(BlockRequest::toDomain).toList();}
 private AuthenticatedActor currentActor(){AuthenticatedActor a=identity.currentActor();if(a==null||a.tenantId()==null||a.actorId()==null)throw new ApiException(401,"An authenticated tenant and actor are required");return a;}
 private static <T>T call(Operation<T> op){try{return op.run();}catch(WorkspaceService.RevisionConflictException e){throw new ApiException(409,e.getMessage(),e);}catch(SecurityException e){throw new ApiException(403,"Access denied",e);}catch(IllegalArgumentException e){throw new ApiException(400,e.getMessage(),e);}}
 @FunctionalInterface private interface Operation<T>{T run();}
 @FunctionalInterface public interface IdentityProvider{AuthenticatedActor currentActor();}
 public record AuthenticatedActor(UUID tenantId,UUID actorId){}
 public record CreateWorkspaceRequest(String name,String description){}
 public record CreateSpaceRequest(String name,String slug){}
 public record CreatePageRequest(UUID parentPageId,String title,List<BlockRequest> blocks){}
 public record UpdatePageRequest(long expectedRevision,String title,List<BlockRequest> blocks,String changeSummary){}
 public record RestorePageRevisionRequest(long sourceRevision,long expectedCurrentRevision){}
 public record BlockRequest(UUID id,String type,String text,int position,Map<String,String> attributes){
  public PageBlock toDomain(){BlockType parsed;try{parsed=BlockType.valueOf(Objects.requireNonNull(type,"block type").trim().toUpperCase(java.util.Locale.ROOT));}catch(RuntimeException e){throw new IllegalArgumentException("Unsupported block type: "+type,e);}return new PageBlock(id==null?UUID.randomUUID():id,parsed,text,position,attributes);}
 }
 public record WorkspaceView(UUID id,String name,String description,Instant createdAt){static WorkspaceView from(Workspace w){return new WorkspaceView(w.id(),w.name(),w.description(),w.createdAt());}}
 public record SpaceView(UUID id,UUID workspaceId,String name,String slug,Instant createdAt){static SpaceView from(Space s){return new SpaceView(s.id(),s.workspaceId(),s.name(),s.slug(),s.createdAt());}}
 public record BlockView(UUID id,String type,String text,int position,Map<String,String> attributes){static BlockView from(PageBlock b){return new BlockView(b.id(),b.type().name(),b.text(),b.position(),b.attributes());}}
 public record PageView(UUID id,UUID workspaceId,UUID spaceId,UUID parentPageId,String title,List<BlockView> blocks,long revision,Instant createdAt,Instant updatedAt){public PageView{blocks=List.copyOf(blocks);}static PageView from(Page p){return new PageView(p.id(),p.workspaceId(),p.spaceId(),p.parentPageId(),p.title(),p.blocks().stream().map(BlockView::from).toList(),p.revision(),p.createdAt(),p.updatedAt());}}
 public record RevisionView(long revision,String title,List<BlockView> blocks,UUID actorId,Instant recordedAt,String changeSummary){public RevisionView{blocks=List.copyOf(blocks);}static RevisionView from(PageRevision r){return new RevisionView(r.revision(),r.title(),r.blocks().stream().map(BlockView::from).toList(),r.actorId(),r.recordedAt(),r.changeSummary());}}
 public static final class ApiException extends RuntimeException{private final int status;public ApiException(int status,String message){super(message);this.status=status;}public ApiException(int status,String message,Throwable cause){super(message,cause);this.status=status;}public int status(){return status;}}
}
