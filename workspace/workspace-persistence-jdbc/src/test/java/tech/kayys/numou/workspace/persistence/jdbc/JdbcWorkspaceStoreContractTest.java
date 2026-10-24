package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;
import tech.kayys.numou.workspace.domain.PageTemplate;
import tech.kayys.numou.workspace.domain.Workspace;
import org.postgresql.ds.PGSimpleDataSource;
import java.time.Instant;
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

 @Test void emptyAttributesRoundTrip() {
  assertEquals(Map.of(),BlockAttributesCodec.decode(BlockAttributesCodec.encode(Map.of())));
 }
 @Test void malformedAttributeEncodingIsRejected() {
  assertThrows(IllegalStateException.class,()->BlockAttributesCodec.decode("missing-separator"));
 }
}
