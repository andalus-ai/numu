package tech.kayys.numou.workspace.application;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant; import java.util.List; import java.util.Optional; import java.util.UUID;
/** Adapter ports keep the workspace domain independent from persistence, auth, files, and other Numou modules. */
public final class WorkspacePorts {
 private WorkspacePorts() {}
 public interface WorkspaceRepository { boolean save(Workspace workspace); Optional<Workspace> findWorkspace(UUID tenantId,UUID workspaceId); }
 public interface SpaceRepository { boolean save(Space space); Optional<Space> find(UUID tenantId,UUID workspaceId,UUID spaceId); boolean slugExists(UUID tenantId,UUID workspaceId,String slug); }
 public interface PageRepository {
  Optional<Page> findPage(UUID tenantId,UUID pageId); List<Page> findChildren(UUID tenantId,UUID spaceId,UUID parentPageId);
  /** Atomically persists page and immutable revision; expectedRevision=0 means create-only. */
  boolean saveWithRevision(Page page,PageRevision revision,long expectedRevision);
  List<PageRevision> history(UUID tenantId,UUID pageId);
 }
 public interface PageLinkRepository {
  boolean save(PageLink link);
  Optional<PageLink> find(UUID tenantId,UUID linkId);
  boolean delete(UUID tenantId,UUID linkId);
  List<PageLink> outgoing(UUID tenantId,UUID sourcePageId);
  List<PageLink> backlinks(UUID tenantId,UUID targetPageId);
 }
 public interface PageTemplateRepository {
  /** Must enforce uniqueness of normalized template name within tenant/workspace. */
  boolean save(PageTemplate template);
  Optional<PageTemplate> find(UUID tenantId,UUID templateId);
  List<PageTemplate> findByWorkspace(UUID tenantId,UUID workspaceId);
 }
 public interface AccessPolicy { /** Must check actor membership and resource-level permission, not merely tenant equality. */ void require(UUID tenantId,UUID actorId,Resource resource,UUID resourceId,Action action); }
 public interface AuditSink { /** Must durably record or fail; do not silently drop audit entries. */ void record(AuditEntry entry); }
 public interface AttachmentPolicy { /** Verifies that actor may reference this file in this tenant. */ String requireReadableFile(UUID tenantId,UUID actorId,String fileId); }
 public enum Resource { WORKSPACE, SPACE, PAGE, ATTACHMENT }
 public enum Action { CREATE, READ, UPDATE, DELETE, ATTACH }
 public record AuditEntry(UUID tenantId,UUID actorId,String action,String resourceType,UUID resourceId,long revision,String summary,Instant occurredAt) {}
}
