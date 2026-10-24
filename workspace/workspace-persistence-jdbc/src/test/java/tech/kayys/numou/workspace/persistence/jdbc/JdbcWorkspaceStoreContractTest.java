package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.domain.BlockType;
import tech.kayys.numou.workspace.domain.Page;
import tech.kayys.numou.workspace.domain.PageBlock;
import tech.kayys.numou.workspace.domain.PageRevision;
import tech.kayys.numou.workspace.domain.Workspace;
import org.postgresql.ds.PGSimpleDataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWorkspaceStoreContractTest {
 @Test void blockAttributesRoundTripDelimitersUnicodeAndEmptyValues() {
  Map<String,String> attributes=Map.of("referenceType","finance.invoice","referenceId","a:b;c","unicode","café 東京","empty","");
  assertEquals(attributes,BlockAttributesCodec.decode(BlockAttributesCodec.encode(attributes)));
 }

 @Test void auditedWorkspaceMutationRejectsMismatchedActionBeforeOpeningConnection() {
  PGSimpleDataSource dataSource = new PGSimpleDataSource();
  JdbcWorkspaceStore store = new JdbcWorkspaceStore(dataSource);
  UUID tenant = UUID.randomUUID();
  UUID actor = UUID.randomUUID();
  Instant now = Instant.parse("2026-10-24T00:00:00Z");
  Workspace workspace = Workspace.create(tenant, "Audit action", "", now);
  AuditEntry wrongAction = new AuditEntry(tenant, actor, "WORKSPACE_DELETED", "WORKSPACE",
    workspace.id(), 0, "incorrect action", now);

  IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
    () -> store.saveWorkspace(workspace, wrongAction));
  assertEquals("audit action does not match mutation action", error.getMessage());
 }

 @Test void auditedWorkspaceMutationRejectsUnexpectedRevisionBeforeOpeningConnection() {
  PGSimpleDataSource dataSource = new PGSimpleDataSource();
  JdbcWorkspaceStore store = new JdbcWorkspaceStore(dataSource);
  UUID tenant = UUID.randomUUID();
  UUID actor = UUID.randomUUID();
  Instant now = Instant.parse("2026-10-24T00:00:00Z");
  Workspace workspace = Workspace.create(tenant, "Audit revision", "", now);
  AuditEntry wrongRevision = new AuditEntry(tenant, actor, "WORKSPACE_CREATED", "WORKSPACE",
    workspace.id(), 1, "incorrect revision", now);

  IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
    () -> store.saveWorkspace(workspace, wrongRevision));
  assertEquals("audit revision does not match mutation revision", error.getMessage());
 }


 @Test void auditedPageCreationRejectsUpdateActionBeforeOpeningConnection() {
  PGSimpleDataSource dataSource = new PGSimpleDataSource();
  JdbcWorkspaceStore store = new JdbcWorkspaceStore(dataSource);
  UUID tenant = UUID.randomUUID();
  UUID actor = UUID.randomUUID();
  Instant now = Instant.parse("2026-10-24T00:00:00Z");
  Workspace workspace = Workspace.create(tenant, "Page audit action", "", now);
  UUID spaceId = UUID.randomUUID();
  PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH, "content", 0, Map.of());
  Page page = Page.create(tenant, workspace.id(), spaceId, null, "Page", List.of(block), now);
  PageRevision revision = PageRevision.snapshot(page, actor, now, "initial");
  AuditEntry wrongAction = new AuditEntry(tenant, actor, "PAGE_UPDATED", "PAGE",
    page.id(), page.revision(), "incorrect action", now);

  IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
    () -> store.savePageWithRevision(page, revision, 0, wrongAction));
  assertEquals("audit action does not match mutation action", error.getMessage());
 }

 @Test void auditedPageMutationRejectsRevisionMismatchBeforeOpeningConnection() {
  PGSimpleDataSource dataSource = new PGSimpleDataSource();
  JdbcWorkspaceStore store = new JdbcWorkspaceStore(dataSource);
  UUID tenant = UUID.randomUUID();
  UUID actor = UUID.randomUUID();
  Instant now = Instant.parse("2026-10-24T00:00:00Z");
  Workspace workspace = Workspace.create(tenant, "Page audit revision", "", now);
  PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH, "content", 0, Map.of());
  Page page = Page.create(tenant, workspace.id(), UUID.randomUUID(), null, "Page", List.of(block), now);
  PageRevision revision = PageRevision.snapshot(page, actor, now, "initial");
  AuditEntry wrongRevision = new AuditEntry(tenant, actor, "PAGE_CREATED", "PAGE",
    page.id(), page.revision() + 1, "incorrect revision", now);

  IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
    () -> store.savePageWithRevision(page, revision, 0, wrongRevision));
  assertEquals("audit revision does not match mutation revision", error.getMessage());
 }

 @Test void emptyAttributesRoundTrip() {
  assertEquals(Map.of(),BlockAttributesCodec.decode(BlockAttributesCodec.encode(Map.of())));
 }
 @Test void malformedAttributeEncodingIsRejected() {
  assertThrows(IllegalStateException.class,()->BlockAttributesCodec.decode("missing-separator"));
 }
}
