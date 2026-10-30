package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.workspace.domain.*;
import tech.kayys.numou.workspace.application.WorkspacePorts.AuditEntry;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in PostgreSQL integration coverage. Set NUMOU_WORKSPACE_TEST_JDBC_URL (and optionally
 * NUMOU_WORKSPACE_TEST_DB_USER / NUMOU_WORKSPACE_TEST_DB_PASSWORD) to a disposable test database.
 */
class JdbcWorkspaceStorePostgresTest {
    private static final List<String> MIGRATIONS = List.of(
            "/db/migration/V1__workspace_foundation.sql",
            "/db/migration/V2__workspace_page_links.sql",
            "/db/migration/V3__workspace_page_templates.sql",
            "/db/migration/V4__workspace_page_search_indexes.sql",
            "/db/migration/V5__workspace_case_insensitive_space_slugs.sql",
            "/db/migration/V6__workspace_template_name_integrity.sql",
            "/db/migration/V7__workspace_domain_invariants.sql",
            "/db/migration/V8__workspace_page_parent_invariant.sql",
            "/db/migration/V9__workspace_audit_invariants.sql",
            "/db/migration/V10__workspace_page_hierarchy_cycle_invariant.sql",
            "/db/migration/V11__workspace_revision_snapshot_integrity.sql",
            "/db/migration/V12__workspace_revision_blocks_immutable.sql",
            "/db/migration/V13__workspace_audit_action_semantics.sql",
            "/db/migration/V14__workspace_audit_resource_actor_integrity.sql",
            "/db/migration/V15__workspace_audit_history_append_only.sql",
            "/db/migration/V16__workspace_structured_databases.sql",
            "/db/migration/V17__workspace_database_views.sql",
            "/db/migration/V18__workspace_board_view_grouping.sql",
            "/db/migration/V19__workspace_saved_view_revision.sql",
            "/db/migration/V20__workspace_staged_schema_migrations.sql",
            "/db/migration/V21__workspace_schema_migration_worker_leases.sql",
            "/db/migration/V22__workspace_staged_migration_scope_integrity.sql",
            "/db/migration/V23__workspace_page_comment_threads.sql",
            "/db/migration/V24__workspace_comment_mention_outbox.sql");

    private String jdbcUrl;
    private String schema;
    private DataSource dataSource;
    private JdbcWorkspaceStore store;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = System.getenv("NUMOU_WORKSPACE_TEST_JDBC_URL");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                "PostgreSQL integration tests require NUMOU_WORKSPACE_TEST_JDBC_URL");
        String user = System.getenv("NUMOU_WORKSPACE_TEST_DB_USER");
        String password = System.getenv("NUMOU_WORKSPACE_TEST_DB_PASSWORD");
        schema = "workspace_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = connect(jdbcUrl, user, password); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        }
        String scopedUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL(scopedUrl);
        if (user != null && !user.isBlank()) pg.setUser(user);
        if (password != null) pg.setPassword(password);
        dataSource = pg;
        migrate(dataSource);
        store = new JdbcWorkspaceStore(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (schema == null || jdbcUrl == null || jdbcUrl.isBlank()) return;
        String user = System.getenv("NUMOU_WORKSPACE_TEST_DB_USER");
        String password = System.getenv("NUMOU_WORKSPACE_TEST_DB_PASSWORD");
        try (Connection c = connect(jdbcUrl, user, password); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void pageCommentThreadWritesAndAuditAreAtomicAndTenantScoped() {
        Instant now = Instant.parse("2026-10-30T00:00:00Z");
        UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Comments", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "General", "general", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Discussion", List.of(), now);
        PageRevision revision = PageRevision.snapshot(page, actor, now, "Created");
        assertTrue(store.saveWithRevision(page, revision, 0));

        PageCommentThread thread = PageCommentThread.open(tenant, page.id(), actor, "Please review.", now);
        AuditEntry createdAudit = new AuditEntry(tenant, actor, "PAGE_COMMENT_THREAD_CREATED",
                "PAGE_COMMENT_THREAD", thread.id(), 1, page.title(), now);
        assertTrue(store.createThread(thread, createdAudit));
        assertEquals(1, store.findThread(tenant, page.id(), thread.id()).orElseThrow().comments().size());

        PageComment reply = PageComment.reply(tenant, page.id(), thread.id(),
                thread.comments().get(0).id(), actor, "Reviewed.", now.plusSeconds(1));
        AuditEntry replyAudit = new AuditEntry(tenant, actor, "PAGE_COMMENT_REPLIED",
                "PAGE_COMMENT", reply.id(), 0, page.title(), now.plusSeconds(1));
        assertTrue(store.appendComment(tenant, page.id(), thread.id(), reply, replyAudit));
        assertEquals(2, store.findThreads(tenant, page.id()).get(0).comments().size());
        assertTrue(store.findThread(UUID.randomUUID(), page.id(), thread.id()).isEmpty());

        PageCommentThread resolved = thread.withComments(List.of(thread.comments().get(0), reply))
                .resolve(actor, now.plusSeconds(2));
        AuditEntry resolvedAudit = new AuditEntry(tenant, actor, "PAGE_COMMENT_THREAD_RESOLVED",
                "PAGE_COMMENT_THREAD", thread.id(), 2, page.title(), now.plusSeconds(2));
        assertTrue(store.updateThreadResolution(resolved, 1, resolvedAudit));
        assertTrue(store.findThread(tenant, page.id(), thread.id()).orElseThrow().resolved());
        assertFalse(store.updateThreadResolution(resolved, 1, resolvedAudit),
                "stale resolution updates must fail their revision compare-and-set");

        try (Connection connection = dataSource.getConnection();
             var query = connection.prepareStatement(
                     "SELECT count(*) FROM workspace_audit_events WHERE tenant_id=? AND resource_type IN ('PAGE_COMMENT','PAGE_COMMENT_THREAD')")) {
            query.setObject(1, tenant);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(3, result.getLong(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not verify comment audit rows", e);
        }
    }

    @Test
    void spaceSlugsAreUniqueIgnoringCaseAtTheDatabaseBoundary() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Slug uniqueness", "", now);
        assertTrue(store.save(workspace));

        Space canonical = Space.create(tenant, workspace.id(), "Operations", "operations", now);
        assertTrue(store.save(canonical));

        // Application pre-checks are not enough under concurrent writes. PostgreSQL must
        // reject a case-only collision even when callers bypass slugExists().
        Space caseVariant = Space.create(tenant, workspace.id(), "Operations duplicate", "OPERATIONS", now);
        assertFalse(store.save(caseVariant));
        assertTrue(store.find(tenant, workspace.id(), caseVariant.id()).isEmpty());
    }

    @Test
    void workspaceAndSpaceNavigationQueriesAreTenantScopedAndOrdered() {
        Instant first = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        Workspace older = Workspace.create(tenant, "Older", "", first);
        Workspace newer = Workspace.create(tenant, "Newer", "", first.plusSeconds(1));
        Workspace foreign = Workspace.create(otherTenant, "Foreign", "", first);
        assertTrue(store.save(newer));
        assertTrue(store.save(foreign));
        assertTrue(store.save(older));

        assertEquals(List.of(older.id(), newer.id()),
                store.findByTenant(tenant).stream().map(Workspace::id).toList());
        assertEquals(List.of(foreign.id()),
                store.findByTenant(otherTenant).stream().map(Workspace::id).toList());

        Space firstSpace = Space.create(tenant, older.id(), "General", "general", first);
        Space secondSpace = Space.create(tenant, older.id(), "Operations", "operations", first.plusSeconds(1));
        Space foreignSpace = Space.create(otherTenant, foreign.id(), "Private", "private", first);
        assertTrue(store.save(secondSpace));
        assertTrue(store.save(foreignSpace));
        assertTrue(store.save(firstSpace));

        assertEquals(List.of(firstSpace.id(), secondSpace.id()),
                store.findByWorkspace(tenant, older.id()).stream().map(Space::id).toList());
        assertTrue(store.findByWorkspace(otherTenant, older.id()).isEmpty());
    }

    @Test
    void databaseRejectsTemplateNameNormalizationDrift() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Template integrity", "", now);
        assertTrue(store.save(workspace));

        PageTemplate template = PageTemplate.create(tenant, workspace.id(), actor,
                "Incident Runbook", "Runbook", List.of(), now);
        assertTrue(store.save(template));

        // Exercise the database boundary directly: adapters must not be the only
        // enforcement point for the normalized key used by template uniqueness.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_page_templates SET name_normalized=? WHERE tenant_id=? AND id=?")) {
            p.setString(1, "different-key");
            p.setObject(2, tenant);
            p.setObject(3, template.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }
        assertEquals("Incident Runbook", store.findTemplate(tenant, template.id()).orElseThrow().name());
    }

    @Test
    void databaseRejectsNonCanonicalSlugsAndBlankDomainNames() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Invariant checks", "", now);
        assertTrue(store.save(workspace));

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_spaces(id,tenant_id,workspace_id,name,slug,created_at) VALUES(?,?,?,?,?,?)")) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, tenant);
            p.setObject(3, workspace.id());
            p.setString(4, "Invalid slug");
            p.setString(5, "UPPERCASE-SLUG");
            p.setTimestamp(6, java.sql.Timestamp.from(now));
            assertThrows(SQLException.class, p::executeUpdate,
                    "the database must enforce the same canonical slug rule as the domain");
        }

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_workspaces SET name='   ' WHERE tenant_id=? AND id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, workspace.id());
            assertThrows(SQLException.class, p::executeUpdate,
                    "direct SQL must not bypass required workspace names");
        }
        assertEquals("Invariant checks", store.findWorkspace(tenant, workspace.id()).orElseThrow().name());
    }

    @Test
    void templateRoundTripIsAtomicAndScopedToTenant() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Operations", "", now);
        assertTrue(store.save(workspace));

        PageTemplate template = PageTemplate.create(tenant, workspace.id(), actor, "Runbook", "Incident runbook",
                List.of(new PageBlock(UUID.randomUUID(), BlockType.HEADING, "Response", 0, Map.of()),
                        new PageBlock(UUID.randomUUID(), BlockType.CALLOUT, "Escalate when needed", 1,
                                Map.of("tone", "warning", "source", "on-call;東京"))), now);
        assertTrue(store.save(template));

        PageTemplate loaded = store.findTemplate(tenant, template.id()).orElseThrow();
        assertEquals(template.name(), loaded.name());
        assertEquals(template.title(), loaded.title());
        assertEquals(template.blocks(), loaded.blocks());
        assertTrue(store.findTemplate(UUID.randomUUID(), template.id()).isEmpty());
        assertEquals(List.of(template.id()), store.findByWorkspace(tenant, workspace.id()).stream().map(PageTemplate::id).toList());

        PageTemplate duplicateName = new PageTemplate(UUID.randomUUID(), tenant, workspace.id(), actor,
                "runbook", "Duplicate", List.of(), now, now);
        assertFalse(store.save(duplicateName));
        assertEquals(1, store.findByWorkspace(tenant, workspace.id()).size());
    }

    @Test
    void pageRevisionLinksAndFullTextSearchPersistAcrossConnections() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Engineering", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Platform", "platform", now);
        assertTrue(store.save(space));

        PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH,
                "PostgreSQL recovery checklist", 0, Map.of("origin", "integration-test"));
        Page page = Page.create(tenant, workspace.id(), space.id(), null,
                "Database recovery", List.of(block), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        Page reloaded = store.findPage(tenant, page.id()).orElseThrow();
        assertEquals(page, reloaded);
        assertEquals(1, store.history(tenant, page.id()).size());
        assertTrue(store.findPage(UUID.randomUUID(), page.id()).isEmpty());

        Page revised = page.revise("Database recovery checklist v2", page.blocks(), now.plusSeconds(1));
        assertTrue(store.saveWithRevision(revised, PageRevision.snapshot(revised, actor, now.plusSeconds(1), "clarified"), 1));
        Page stale = page.revise("Stale concurrent edit", page.blocks(), now.plusSeconds(2));
        assertFalse(store.saveWithRevision(stale, PageRevision.snapshot(stale, actor, now.plusSeconds(2), "stale"), 1));
        assertEquals(2, store.history(tenant, page.id()).size());

        Page invalidAtStorageBoundary = Page.create(tenant, workspace.id(), space.id(), null,
                "x".repeat(501), List.of(), now);
        assertThrows(IllegalStateException.class, () -> store.saveWithRevision(invalidAtStorageBoundary,
                PageRevision.snapshot(invalidAtStorageBoundary, actor, now, "should rollback"), 0));
        assertTrue(store.findPage(tenant, invalidAtStorageBoundary.id()).isEmpty());

        PageLink link = PageLink.create(tenant, page.id(),
                createTargetPage(tenant, workspace, space, actor, now), actor, "Related recovery guide", now);
        assertTrue(store.save(link));
        assertEquals(List.of(link), store.outgoing(tenant, page.id()));
        assertEquals(List.of(link), store.backlinks(tenant, link.targetPageId()));

        List<PageSearchHit> hits = store.searchPages(tenant, workspace.id(), null, "recovery checklist", 10);
        assertTrue(hits.stream().anyMatch(hit -> hit.pageId().equals(page.id())));
        assertTrue(store.searchPages(UUID.randomUUID(), workspace.id(), null, "recovery", 10).isEmpty());
        assertTrue(store.searchPages(tenant, workspace.id(), space.id(), "recovery", 10)
                .stream().allMatch(hit -> hit.spaceId().equals(space.id())));
    }


    @Test
    void databaseEnforcesRevisionSnapshotConsistencyAndImmutability() throws SQLException {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Revision snapshot integrity", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "revision-snapshots", now);
        assertTrue(store.save(space));

        PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH, "Current content", 0, Map.of());
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Current title", List.of(block), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        // Direct SQL cannot append a snapshot with a stale revision number or title.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_page_revisions(tenant_id,page_id,revision,title,actor_id,recorded_at,change_summary) VALUES(?,?,?,?,?,?,?)")) {
            p.setObject(1, tenant);
            p.setObject(2, page.id());
            p.setLong(3, 1);
            p.setString(4, "Forged title");
            p.setObject(5, actor);
            p.setTimestamp(6, java.sql.Timestamp.from(now.plusSeconds(1)));
            p.setString(7, "mismatched title");
            assertThrows(SQLException.class, p::executeUpdate);

            p.setLong(3, 2);
            p.setString(4, "Current title");
            p.setString(7, "mismatched revision");
            assertThrows(SQLException.class, p::executeUpdate);
        }

        // A direct page update must not commit unless its matching revision is written too.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_pages SET title=? WHERE tenant_id=? AND id=?")) {
            c.setAutoCommit(false);
            p.setString(1, "Unversioned title");
            p.setObject(2, tenant);
            p.setObject(3, page.id());
            assertEquals(1, p.executeUpdate());
            assertThrows(SQLException.class, c::commit);
            c.rollback();
        }

        // The current page blocks and the current revision's blocks must agree at commit.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_page_blocks SET text_content=? WHERE tenant_id=? AND page_id=? AND block_id=?")) {
            c.setAutoCommit(false);
            p.setString(1, "Unversioned content");
            p.setObject(2, tenant);
            p.setObject(3, page.id());
            p.setObject(4, block.id());
            assertEquals(1, p.executeUpdate());
            assertThrows(SQLException.class, c::commit);
            c.rollback();
        }

        // Historical revision headers are immutable, even for direct SQL callers.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_page_revisions SET title=? WHERE tenant_id=? AND page_id=? AND revision=1")) {
            p.setString(1, "Rewritten history");
            p.setObject(2, tenant);
            p.setObject(3, page.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }

        List<PageRevision> history = store.history(tenant, page.id());
        assertEquals(1, history.size());
        assertEquals("Current title", history.get(0).title());
        assertEquals(1, history.get(0).revision());
        assertEquals(List.of(block), history.get(0).blocks());
        assertEquals(List.of(block), store.findPage(tenant, page.id()).orElseThrow().blocks());
    }

    @Test
    void pageDeletionCanCascadeThroughImmutableRevisionHistory() throws SQLException {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Revision cascade", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "revision-cascade", now);

        assertTrue(store.save(space));
        PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH, "Retained until page deletion", 0,
                Map.of("origin", "cascade-test"));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Cascade", List.of(block), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "DELETE FROM workspace_pages WHERE tenant_id=? AND id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, page.id());
            assertEquals(1, p.executeUpdate());
        }

        assertEquals(0, countRows("workspace_page_revisions", tenant, page.id()));
        assertEquals(0, countRows("workspace_page_revision_blocks", tenant, page.id()));
        assertEquals(0, countRows("workspace_page_blocks", tenant, page.id()));
    }

    private int countRows(String table, UUID tenant, UUID pageId) throws SQLException {
        // Table names are fixed by this test; bind all row identifiers as parameters.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND page_id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, pageId);
            try (java.sql.ResultSet rs = p.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    @Test
    void databaseProtectsRevisionBlockHistoryFromDirectMutation() throws SQLException {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Immutable revision blocks", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "immutable-revisions", now);
        assertTrue(store.save(space));

        PageBlock block = new PageBlock(UUID.randomUUID(), BlockType.PARAGRAPH, "Historical content", 0,
                Map.of("origin", "immutable-test"));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "History", List.of(block), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_page_revision_blocks SET text_content=? WHERE tenant_id=? AND page_id=? AND revision=1 AND block_id=?")) {
            p.setString(1, "rewritten history");
            p.setObject(2, tenant);
            p.setObject(3, page.id());
            p.setObject(4, block.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "DELETE FROM workspace_page_revision_blocks WHERE tenant_id=? AND page_id=? AND revision=1 AND block_id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, page.id());
            p.setObject(3, block.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "DELETE FROM workspace_page_revisions WHERE tenant_id=? AND page_id=? AND revision=1")) {
            p.setObject(1, tenant);
            p.setObject(2, page.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }

        // An extra row cannot be injected into the current revision if it differs from live content.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_page_revision_blocks(tenant_id,page_id,revision,block_id,block_type,text_content,position,attributes_encoded) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setObject(1, tenant);
            p.setObject(2, page.id());
            p.setLong(3, page.revision());
            p.setObject(4, UUID.randomUUID());
            p.setString(5, BlockType.PARAGRAPH.name());
            p.setString(6, "forged extra block");
            p.setInt(7, 1);
            p.setString(8, "");
            assertThrows(SQLException.class, p::executeUpdate);
        }

        assertEquals(List.of(block), store.history(tenant, page.id()).get(0).blocks());
        assertEquals(List.of(block), store.findPage(tenant, page.id()).orElseThrow().blocks());
    }

    @Test
    void databaseRejectsPageHierarchyCycles() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Hierarchy integrity", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "hierarchy-docs", now);
        assertTrue(store.save(space));

        Page root = Page.create(tenant, workspace.id(), space.id(), null, "Root", List.of(), now);
        assertTrue(store.saveWithRevision(root, PageRevision.snapshot(root, actor, now, "root"), 0));
        Page child = Page.create(tenant, workspace.id(), space.id(), root.id(), "Child", List.of(), now);
        assertTrue(store.saveWithRevision(child, PageRevision.snapshot(child, actor, now, "child"), 0));

        // Direct SQL bypasses the immutable-parent application update path.
        // The database trigger must still reject a cycle, and leave the hierarchy unchanged.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_pages SET parent_page_id=? WHERE tenant_id=? AND id=?")) {
            p.setObject(1, child.id());
            p.setObject(2, tenant);
            p.setObject(3, root.id());
            assertThrows(SQLException.class, p::executeUpdate);
        }
        assertNull(store.findPage(tenant, root.id()).orElseThrow().parentPageId());
        assertEquals(root.id(), store.findPage(tenant, child.id()).orElseThrow().parentPageId());
    }

    @Test
    void concurrentParentChangesCannotCreateHierarchyCycle() throws Exception {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Concurrent hierarchy", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "concurrent-hierarchy", now);
        assertTrue(store.save(space));

        Page first = Page.create(tenant, workspace.id(), space.id(), null, "First", List.of(), now);
        Page second = Page.create(tenant, workspace.id(), space.id(), null, "Second", List.of(), now);
        assertTrue(store.saveWithRevision(first, PageRevision.snapshot(first, actor, now, "first"), 0));
        assertTrue(store.saveWithRevision(second, PageRevision.snapshot(second, actor, now, "second"), 0));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstUpdate = executor.submit(
                    () -> attemptParentChange(dataSource, tenant, first.id(), second.id(), ready, start));
            Future<Boolean> secondUpdate = executor.submit(
                    () -> attemptParentChange(dataSource, tenant, second.id(), first.id(), ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "both PostgreSQL transactions must be ready");
            start.countDown();
            boolean firstChanged = firstUpdate.get(20, TimeUnit.SECONDS);
            boolean secondChanged = secondUpdate.get(20, TimeUnit.SECONDS);

            assertTrue(firstChanged ^ secondChanged,
                    "exactly one concurrent reparenting may commit; the other would close a cycle");
            UUID firstParent = store.findPage(tenant, first.id()).orElseThrow().parentPageId();
            UUID secondParent = store.findPage(tenant, second.id()).orElseThrow().parentPageId();
            assertFalse(first.id().equals(secondParent) && second.id().equals(firstParent),
                    "persisted hierarchy must not contain the two-page cycle");
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean attemptParentChange(
            DataSource source,
            UUID tenant,
            UUID pageId,
            UUID parentId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            ready.countDown();
            start.await();
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "UPDATE workspace_pages SET parent_page_id=? WHERE tenant_id=? AND id=?")) {
                statement.setObject(1, parentId);
                statement.setObject(2, tenant);
                statement.setObject(3, pageId);
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                if (!"23514".equals(e.getSQLState())) {
                    throw e;
                }
                return false;
            }
        }
    }


    @Test
    void databaseRejectsAuditRowsForMissingResourcesAndMismatchedPageActors() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID otherActor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Audit resource integrity", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Operations", "operations", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Audited page", List.of(), now);
        PageRevision revision = PageRevision.snapshot(page, actor, now, "created");
        assertTrue(store.saveWithRevision(page, revision, 0));

        assertAuditInsertRejected(tenant, actor, "WORKSPACE_CREATED", "WORKSPACE",
                UUID.randomUUID(), 0, now);

        UUID foreignTenant = UUID.randomUUID();
        Workspace foreignWorkspace = Workspace.create(foreignTenant, "Foreign tenant workspace", "", now);
        assertTrue(store.save(foreignWorkspace));
        assertAuditInsertRejected(tenant, actor, "WORKSPACE_CREATED", "WORKSPACE",
                foreignWorkspace.id(), 0, now);

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_audit_events(tenant_id,actor_id,action,resource_type,resource_id,revision,summary,occurred_at) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setObject(1, tenant);
            p.setObject(2, otherActor);
            p.setString(3, "PAGE_CREATED");
            p.setString(4, "PAGE");
            p.setObject(5, page.id());
            p.setLong(6, 1);
            p.setString(7, "forged actor");
            p.setTimestamp(8, java.sql.Timestamp.from(now));
            assertThrows(SQLException.class, p::executeUpdate,
                    "audit actor must match the immutable page revision actor");
        }
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void databaseRejectsInvalidAuditMetadata() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // Audit validation must hold even when a caller bypasses the JDBC adapter.
        assertAuditInsertRejected(tenant, actor, "   ", "WORKSPACE", UUID.randomUUID(), 0, now);
        assertAuditInsertRejected(tenant, actor, "WORKSPACE_CREATED", "   ", UUID.randomUUID(), 0, now);
        assertAuditInsertRejected(tenant, actor, "WORKSPACE_CREATED", "WORKSPACE", UUID.randomUUID(), -1, now);
        assertEquals(0, countAuditEvents(tenant));
    }


    @Test
    void databaseRejectsKnownAuditActionsWithMismatchedResourceOrRevision() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        // These checks must protect direct SQL writers as well as JdbcWorkspaceStore.
        assertAuditInsertRejected(tenant, actor, "WORKSPACE_CREATED", "PAGE", UUID.randomUUID(), 0, now);
        assertAuditInsertRejected(tenant, actor, "SPACE_CREATED", "SPACE", UUID.randomUUID(), 1, now);
        assertAuditInsertRejected(tenant, actor, "PAGE_CREATED", "PAGE", UUID.randomUUID(), 2, now);
        assertAuditInsertRejected(tenant, actor, "PAGE_UPDATED", "PAGE", UUID.randomUUID(), 1, now);
        assertAuditInsertRejected(tenant, actor, "PAGE_TEMPLATE_CREATED", "PAGE_TEMPLATE", UUID.randomUUID(), 0, now);
        assertAuditInsertRejected(tenant, actor, "PAGE_LINK_CREATED", "PAGE_LINK", UUID.randomUUID(), 1, now);
        assertAuditInsertRejected(tenant, actor, "PAGE_LINK_REMOVED", "WORKSPACE", UUID.randomUUID(), 0, now);
        assertEquals(0, countAuditEvents(tenant));
    }

    private void assertAuditInsertRejected(UUID tenant, UUID actor, String action, String resourceType,
                                           UUID resourceId, long revision, Instant occurredAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_audit_events(tenant_id,actor_id,action,resource_type,resource_id,revision,summary,occurred_at) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setObject(1, tenant);
            p.setObject(2, actor);
            p.setString(3, action);
            p.setString(4, resourceType);
            p.setObject(5, resourceId);
            p.setLong(6, revision);
            p.setString(7, "direct SQL invariant test");
            p.setTimestamp(8, java.sql.Timestamp.from(occurredAt));
            assertThrows(SQLException.class, p::executeUpdate);
        }
    }

    @Test
    void rejectsAuditMetadataThatDoesNotDescribeTheMutation() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Audit integrity", "", now);

        AuditEntry crossTenant = new AuditEntry(otherTenant, UUID.randomUUID(),
                "WORKSPACE_CREATED", "WORKSPACE", workspace.id(), 0, "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.saveWorkspace(workspace, crossTenant));
        assertTrue(store.findWorkspace(tenant, workspace.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));
        assertEquals(0, countAuditEvents(otherTenant));

        AuditEntry missingActor = new AuditEntry(tenant, null,
                "WORKSPACE_CREATED", "WORKSPACE", workspace.id(), 0, "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.saveWorkspace(workspace, missingActor));
        assertTrue(store.findWorkspace(tenant, workspace.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));

        AuditEntry wrongType = new AuditEntry(tenant, UUID.randomUUID(),
                "WORKSPACE_CREATED", "SPACE", workspace.id(), 0, "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.saveWorkspace(workspace, wrongType));
        assertTrue(store.findWorkspace(tenant, workspace.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));

        AuditEntry wrongResource = new AuditEntry(tenant, UUID.randomUUID(),
                "WORKSPACE_CREATED", "WORKSPACE", UUID.randomUUID(), 0, "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.saveWorkspace(workspace, wrongResource));
        assertTrue(store.findWorkspace(tenant, workspace.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void rejectsPageAuditActorAndRevisionThatDifferFromTheSnapshot() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID otherActor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Audit snapshot", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Audit page",
                List.of(PageBlock.text(BlockType.PARAGRAPH, "body", 0)), now);
        PageRevision revision = new PageRevision(tenant, page.id(), page.revision(),
                page.title(), page.blocks(), actor, now, "create");

        AuditEntry wrongActor = new AuditEntry(tenant, otherActor, "PAGE_CREATED", "PAGE",
                page.id(), page.revision(), "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.savePageWithRevision(page, revision, 0, wrongActor));

        AuditEntry wrongRevision = new AuditEntry(tenant, actor, "PAGE_CREATED", "PAGE",
                page.id(), page.revision() + 1, "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.savePageWithRevision(page, revision, 0, wrongRevision));

        assertTrue(store.findPage(tenant, page.id()).isEmpty());
        assertTrue(store.history(tenant, page.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void pageHistoryIsIsolatedByTenant() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "History isolation", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Private page",
                List.of(PageBlock.text(BlockType.PARAGRAPH, "private content", 0)), now);
        PageRevision revision = new PageRevision(tenant, page.id(), page.revision(),
                page.title(), page.blocks(), actor, now, "created");
        assertTrue(store.saveWithRevision(page, revision, 0));

        assertEquals(1, store.history(tenant, page.id()).size());
        assertTrue(store.history(otherTenant, page.id()).isEmpty());
    }

    @Test
    void rejectsRevisionSnapshotsThatDifferFromThePage() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Snapshot consistency", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));

        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Authoritative title",
                List.of(PageBlock.text(BlockType.PARAGRAPH, "authoritative body", 0)), now);
        PageRevision mismatched = new PageRevision(tenant, page.id(), page.revision(),
                "Different title", List.of(), actor, now, "forged snapshot");

        assertThrows(IllegalArgumentException.class,
                () -> store.saveWithRevision(page, mismatched, 0));
        assertTrue(store.findPage(tenant, page.id()).isEmpty());
        assertTrue(store.history(tenant, page.id()).isEmpty());

        AuditEntry audit = new AuditEntry(tenant, actor, "PAGE_CREATED", "PAGE",
                page.id(), page.revision(), "create", now);
        assertThrows(IllegalArgumentException.class,
                () -> store.savePageWithRevision(page, mismatched, 0, audit));
        assertTrue(store.findPage(tenant, page.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void pageAndRevisionRollBackWhenAuditInsertFails() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Atomic audit", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Must roll back", List.of(), now);
        PageRevision revision = PageRevision.snapshot(page, actor, now, "initial");
        AuditEntry invalidAudit = new AuditEntry(tenant, actor, "A".repeat(101), "PAGE",
                page.id(), page.revision(), "force audit constraint failure", now);

        assertThrows(IllegalStateException.class,
                () -> store.savePageWithRevision(page, revision, 0, invalidAudit));
        assertTrue(store.findPage(tenant, page.id()).isEmpty(),
                "page row must roll back when its audit insert fails");
        assertTrue(store.history(tenant, page.id()).isEmpty(),
                "revision row must roll back when its audit insert fails");
    }


    @Test
    void workspaceSpaceAndTemplateWritesRollBackWhenAuditFails() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Atomic workspace", "", now);
        AuditEntry invalidAudit = new AuditEntry(tenant, actor, "A".repeat(101), "WORKSPACE",
                workspace.id(), 0, "force audit write failure", now);

        assertThrows(IllegalStateException.class, () -> store.saveWorkspace(workspace, invalidAudit));
        assertTrue(store.findWorkspace(tenant, workspace.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));

        Workspace parent = Workspace.create(tenant, "Parent", "", now);
        assertTrue(store.save(parent));
        Space space = Space.create(tenant, parent.id(), "Atomic space", "atomic-space", now);
        AuditEntry invalidSpaceAudit = new AuditEntry(tenant, actor, "A".repeat(101), "SPACE",
                space.id(), 0, "force audit write failure", now);
        assertThrows(IllegalStateException.class, () -> store.saveSpace(space, invalidSpaceAudit));
        assertTrue(store.find(tenant, parent.id(), space.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));

        PageTemplate template = PageTemplate.create(tenant, parent.id(), actor, "Atomic template", "Template",
                List.of(PageBlock.text(BlockType.PARAGRAPH, "Body", 0)), now);
        AuditEntry invalidTemplateAudit = new AuditEntry(tenant, actor, "A".repeat(101), "PAGE_TEMPLATE",
                template.id(), 1, "force audit write failure", now);
        assertThrows(IllegalStateException.class, () -> store.saveTemplate(template, invalidTemplateAudit));
        assertTrue(store.findTemplate(tenant, template.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void pageUpdateAndLinkMutationsRollBackWhenAuditFails() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Atomic mutations", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page source = Page.create(tenant, workspace.id(), space.id(), null, "Source", List.of(), now);
        PageRevision initial = PageRevision.snapshot(source, actor, now, "initial");
        assertTrue(store.saveWithRevision(source, initial, 0));

        Page target = Page.create(tenant, workspace.id(), space.id(), null, "Target", List.of(), now);
        assertTrue(store.saveWithRevision(target, PageRevision.snapshot(target, actor, now, "initial"), 0));

        AuditEntry invalidAudit = new AuditEntry(tenant, actor, "A".repeat(101), "PAGE",
                source.id(), 2, "force audit write failure", now.plusSeconds(1));
        Page revised = source.revise("Should roll back", List.of(), now.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> store.savePageWithRevision(
                revised, PageRevision.snapshot(revised, actor, now.plusSeconds(1), "update"), 1, invalidAudit));
        assertEquals("Source", store.findPage(tenant, source.id()).orElseThrow().title());
        assertEquals(1, store.history(tenant, source.id()).size());
        assertEquals(0, countAuditEvents(tenant));

        PageLink link = PageLink.create(tenant, source.id(), target.id(), actor, "related", now);
        AuditEntry invalidLinkAudit = new AuditEntry(tenant, actor, "A".repeat(101), "PAGE_LINK",
                link.id(), 0, "force audit write failure", now);
        assertThrows(IllegalStateException.class, () -> store.saveLink(link, invalidLinkAudit));
        assertTrue(store.findLink(tenant, link.id()).isEmpty());
        assertEquals(0, countAuditEvents(tenant));

        assertTrue(store.save(link));
        assertThrows(IllegalStateException.class, () -> store.deleteLink(tenant, link.id(), invalidLinkAudit));
        assertTrue(store.findLink(tenant, link.id()).isPresent(),
                "link deletion must roll back when the audit insert fails");
        assertEquals(0, countAuditEvents(tenant));
    }

    @Test
    void concurrentPageUpdatesAllowOnlyOneWriterForTheSameExpectedRevision() throws Exception {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Concurrent updates", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Initial", List.of(), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        Page candidateA = page.revise("Concurrent writer A", List.of(), now.plusSeconds(1));
        Page candidateB = page.revise("Concurrent writer B", List.of(), now.plusSeconds(1));
        PageRevision revisionA = PageRevision.snapshot(candidateA, actor, now.plusSeconds(1), "writer A");
        PageRevision revisionB = PageRevision.snapshot(candidateB, actor, now.plusSeconds(1), "writer B");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> writeA = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.saveWithRevision(candidateA, revisionA, 1);
            });
            Future<Boolean> writeB = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.saveWithRevision(candidateB, revisionB, 1);
            });
            ready.await();
            start.countDown();
            boolean resultA = writeA.get();
            boolean resultB = writeB.get();

            assertEquals(1, (resultA ? 1 : 0) + (resultB ? 1 : 0),
                    "exactly one writer should win the compare-and-set update");
            Page persisted = store.findPage(tenant, page.id()).orElseThrow();
            assertTrue(List.of("Concurrent writer A", "Concurrent writer B").contains(persisted.title()));
            assertEquals(2, persisted.revision());
            assertEquals(2, store.history(tenant, page.id()).size(),
                    "the losing writer must not create a revision snapshot");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pageCannotBeItsOwnParentAtDomainAndDatabaseBoundaries() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "No self parent", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));

        UUID pageId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Page(pageId, tenant, workspace.id(),
                space.id(), pageId, "Invalid self parent", List.of(), 1, now, now));

        // Exercise the storage invariant independently of the domain constructor.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "INSERT INTO workspace_pages(id,tenant_id,workspace_id,space_id,parent_page_id,title,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setObject(1, pageId);
            p.setObject(2, tenant);
            p.setObject(3, workspace.id());
            p.setObject(4, space.id());
            p.setObject(5, pageId);
            p.setString(6, "Invalid self parent");
            p.setLong(7, 1);
            p.setTimestamp(8, java.sql.Timestamp.from(now));
            p.setTimestamp(9, java.sql.Timestamp.from(now));
            assertThrows(SQLException.class, p::executeUpdate);
        }
        assertTrue(store.findPage(tenant, pageId).isEmpty());
        assertTrue(store.history(tenant, pageId).isEmpty());
    }

    @Test
    void pageUpdateCannotChangeItsWorkspaceSpaceOrParentIdentity() {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Immutable page identity", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Original", List.of(), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));

        UUID otherWorkspaceId = UUID.randomUUID();
        UUID otherSpaceId = UUID.randomUUID();
        Page forged = new Page(page.id(), tenant, otherWorkspaceId, otherSpaceId, UUID.randomUUID(),
                "Identity changed", List.of(PageBlock.text(BlockType.PARAGRAPH, "must not persist", 0)),
                page.revision() + 1, page.createdAt(), now.plusSeconds(1));
        assertFalse(store.saveWithRevision(forged,
                PageRevision.snapshot(forged, actor, now.plusSeconds(1), "forged identity"), page.revision()));

        Page persisted = store.findPage(tenant, page.id()).orElseThrow();
        assertEquals(workspace.id(), persisted.workspaceId());
        assertEquals(space.id(), persisted.spaceId());
        assertNull(persisted.parentPageId());
        assertEquals("Original", persisted.title());
        assertTrue(persisted.blocks().isEmpty());
        assertEquals(1, store.history(tenant, page.id()).size());
    }

    @Test
    void staleAuditedPageUpdateDoesNotWriteAnAuditEvent() throws SQLException {
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Concurrency", "", now);
        assertTrue(store.save(workspace));
        Space space = Space.create(tenant, workspace.id(), "Docs", "docs", now);
        assertTrue(store.save(space));
        Page page = Page.create(tenant, workspace.id(), space.id(), null, "Current", List.of(), now);
        assertTrue(store.saveWithRevision(page, PageRevision.snapshot(page, actor, now, "initial"), 0));
        Page current = page.revise("Current revision", List.of(), now.plusSeconds(1));
        AuditEntry validAudit = new AuditEntry(tenant, actor, "PAGE_UPDATED", "PAGE",
                page.id(), current.revision(), "current update", now.plusSeconds(1));
        assertTrue(store.savePageWithRevision(current,
                PageRevision.snapshot(current, actor, now.plusSeconds(1), "current update"), 1, validAudit));
        long auditAfterCurrentWrite = countAuditEvents(tenant);

        Page stale = page.revise("Stale update", List.of(), now.plusSeconds(2));
        AuditEntry staleAudit = new AuditEntry(tenant, actor, "PAGE_UPDATED", "PAGE",
                page.id(), stale.revision(), "stale update", now.plusSeconds(2));
        assertFalse(store.savePageWithRevision(stale,
                PageRevision.snapshot(stale, actor, now.plusSeconds(2), "stale update"), 1, staleAudit));
        assertEquals("Current revision", store.findPage(tenant, page.id()).orElseThrow().title());
        assertEquals(2, store.history(tenant, page.id()).size());
        assertEquals(auditAfterCurrentWrite, countAuditEvents(tenant),
                "stale optimistic writes must not create audit rows");
    }

    private long countAuditEvents(UUID tenant) throws SQLException {
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "SELECT count(*) FROM workspace_audit_events WHERE tenant_id=?")) {
            p.setObject(1, tenant);
            try (java.sql.ResultSet r = p.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    private UUID createTargetPage(UUID tenant, Workspace workspace, Space space, UUID actor, Instant now) {
        Page target = Page.create(tenant, workspace.id(), space.id(), null, "Recovery guide",
                List.of(PageBlock.text(BlockType.PARAGRAPH, "Steps", 0)), now);
        assertTrue(store.saveWithRevision(target, PageRevision.snapshot(target, actor, now, "initial"), 0));
        return target.id();
    }

    private static Connection connect(String url, String user, String password) throws SQLException {
        if (user == null || user.isBlank()) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private static void migrate(DataSource source) throws IOException, SQLException {
        for (String migration : MIGRATIONS) {
            String sql;
            try (InputStream in = JdbcWorkspaceStorePostgresTest.class.getResourceAsStream(migration)) {
                if (in == null) throw new IllegalStateException("Missing migration resource " + migration);
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // Keep procedural bodies (for example PostgreSQL dollar-quoted functions) intact.
            try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
                for (String statement : splitSqlStatements(sql)) {
                    if (!statement.isBlank()) s.execute(statement);
                }
            }
        }
    }

    /**
     * Splits migration SQL on statement terminators without splitting strings,
     * comments, quoted identifiers, or PostgreSQL dollar-quoted function bodies.
     */
    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        String dollarTag = null;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                current.append(ch);
                if (ch == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                current.append(ch);
                if (ch == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (dollarTag != null) {
                if (sql.startsWith(dollarTag, i)) {
                    current.append(dollarTag);
                    i += dollarTag.length() - 1;
                    dollarTag = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (singleQuoted) {
                current.append(ch);
                if (ch == '\'' && next == '\'') {
                    current.append(next);
                    i++;
                } else if (ch == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                current.append(ch);
                if (ch == '"' && next == '"') {
                    current.append(next);
                    i++;
                } else if (ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                current.append(ch).append(next);
                i++;
                lineComment = true;
            } else if (ch == '/' && next == '*') {
                current.append(ch).append(next);
                i++;
                blockComment = true;
            } else if (ch == '\'') {
                current.append(ch);
                singleQuoted = true;
            } else if (ch == '"') {
                current.append(ch);
                doubleQuoted = true;
            } else if (ch == '$') {
                int end = sql.indexOf('$', i + 1);
                if (end >= 0) {
                    String tag = sql.substring(i, end + 1);
                    if (tag.matches("\\$[A-Za-z_0-9]*\\$")) {
                        current.append(tag);
                        i = end;
                        dollarTag = tag;
                    } else {
                        current.append(ch);
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == ';') {
                statements.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }

    @Test
    void databaseRejectsAuditHistoryUpdatesAndDeletes() throws SQLException {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Workspace workspace = Workspace.create(tenant, "Append-only audit", "", now);
        AuditEntry event = new AuditEntry(tenant, actor, "WORKSPACE_CREATED", "WORKSPACE",
                workspace.id(), 0, workspace.name(), now);
        assertTrue(store.saveWorkspace(workspace, event));

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "UPDATE workspace_audit_events SET summary=? WHERE tenant_id=? AND resource_id=?")) {
            p.setString(1, "tampered summary");
            p.setObject(2, tenant);
            p.setObject(3, workspace.id());
            assertThrows(SQLException.class, p::executeUpdate,
                    "direct SQL must not be able to rewrite an audit event");
        }

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "DELETE FROM workspace_audit_events WHERE tenant_id=? AND resource_id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, workspace.id());
            assertThrows(SQLException.class, p::executeUpdate,
                    "direct SQL must not be able to delete an audit event");
        }

        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement p = c.prepareStatement(
                     "SELECT summary FROM workspace_audit_events WHERE tenant_id=? AND resource_id=?")) {
            p.setObject(1, tenant);
            p.setObject(2, workspace.id());
            try (java.sql.ResultSet rs = p.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Append-only audit", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

}
