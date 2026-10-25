package tech.kayys.numou.workspace.application;

import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
  Optional<PageLink> findLink(UUID tenantId,UUID linkId);
  boolean delete(UUID tenantId,UUID linkId);
  List<PageLink> outgoing(UUID tenantId,UUID sourcePageId);
  List<PageLink> backlinks(UUID tenantId,UUID targetPageId);
 }
 public interface PageSearchRepository {
  /** Full-text search is scoped to a tenant and workspace; null spaceId means all spaces in that workspace. */
  List<PageSearchHit> searchPages(UUID tenantId, UUID workspaceId, UUID spaceId, String query, int limit);
 }
 public interface PageTemplateRepository {
  /** Must enforce uniqueness of normalized template name within tenant/workspace. */
  boolean save(PageTemplate template);
  Optional<PageTemplate> findTemplate(UUID tenantId,UUID templateId);
  List<PageTemplate> findByWorkspace(UUID tenantId,UUID workspaceId);
 }
 public interface AccessPolicy { /** Must check actor membership and resource-level permission, not merely tenant equality. */ void require(UUID tenantId,UUID actorId,Resource resource,UUID resourceId,Action action); }
 public interface AuditSink { /** Must durably record or fail; do not silently drop audit entries. */ void record(AuditEntry entry); }
 /**
  * Optional capability for adapters that can atomically persist a resource mutation and its audit row.
  * Production relational adapters should implement this port; separate save()+record() calls are not atomic.
  */
 public interface AuditedMutationRepository {
  boolean saveWorkspace(Workspace workspace, AuditEntry audit);
  boolean saveSpace(Space space, AuditEntry audit);
  boolean savePageWithRevision(Page page, PageRevision revision, long expectedRevision, AuditEntry audit);
  boolean saveTemplate(PageTemplate template, AuditEntry audit);
  boolean saveLink(PageLink link, AuditEntry audit);
  boolean deleteLink(UUID tenantId, UUID linkId, AuditEntry audit);
 }
 /** Structured workspace data operations must persist the mutation and audit entry in one transaction. */
 public interface StructuredDatabaseRepository {
  boolean saveDatabase(WorkspaceDatabase database, AuditEntry audit);
  Optional<WorkspaceDatabase> findDatabase(UUID tenantId, UUID databaseId);
  boolean saveTable(DatabaseTable table, AuditEntry audit);
  /** Atomically advances a table schema and appends its audit event only when expectedSchemaVersion still matches. */
  boolean updateTableSchema(DatabaseTable table, long expectedSchemaVersion, AuditEntry audit);
  /** Active records only; adapters must bound synchronous migrations and return deterministic order. */
  List<DatabaseRecord> findRecordsForSchemaMigration(UUID tenantId, UUID databaseId, UUID tableId);
  /** Atomically updates schema, migrated record revisions, and every corresponding audit row. */
  boolean migrateTableSchema(DatabaseTable table, long expectedSchemaVersion, List<DatabaseRecord> migratedRecords,
                             AuditEntry schemaAudit, List<AuditEntry> recordAudits);
  Optional<DatabaseTable> findTable(UUID tenantId, UUID databaseId, UUID tableId);
  Optional<DatabaseRecord> findRecord(UUID tenantId, UUID recordId);
  /** expectedRevision=0 means create-only; positive values require an atomic compare-and-swap. */
  boolean saveRecord(DatabaseRecord record, long expectedRevision, AuditEntry audit);
  /** Exact-match JSONB filters; results must be tenant/database/table scoped and deterministically ordered. */
  DatabaseRecordPage queryRecords(UUID tenantId, UUID databaseId, UUID tableId, java.util.Map<String, Object> filters, int limit, int offset);
  /** Query with validated typed ordering; legacy adapters may retain deterministic default order. */
  default DatabaseRecordPage queryRecords(UUID tenantId, UUID databaseId, UUID tableId, java.util.Map<String, Object> filters, List<DatabaseViewSort> sorts, int limit, int offset) { return queryRecords(tenantId, databaseId, tableId, filters, limit, offset); }
  boolean saveView(DatabaseView view, AuditEntry audit);
  boolean updateView(DatabaseView view, long expectedRevision, AuditEntry audit);
  Optional<DatabaseView> findView(UUID tenantId, UUID databaseId, UUID viewId);
  List<DatabaseView> findViews(UUID tenantId, UUID databaseId, UUID tableId, UUID actorId);
  /** Deletes a personal view and its audit entry atomically, scoped to tenant/database/owner. */
  boolean deleteView(UUID tenantId, UUID databaseId, UUID viewId, UUID actorId, long expectedRevision, AuditEntry audit);
 }
 public interface AttachmentPolicy { /** Verifies that actor may reference this file in this tenant. */ String requireReadableFile(UUID tenantId,UUID actorId,String fileId); }
 public enum Resource { WORKSPACE, SPACE, PAGE, ATTACHMENT, DATABASE, DATABASE_TABLE, DATABASE_RECORD }
 public enum Action { CREATE, READ, UPDATE, DELETE, ATTACH }
 public record AuditEntry(UUID tenantId,UUID actorId,String action,String resourceType,UUID resourceId,long revision,String summary,Instant occurredAt) {}
}
