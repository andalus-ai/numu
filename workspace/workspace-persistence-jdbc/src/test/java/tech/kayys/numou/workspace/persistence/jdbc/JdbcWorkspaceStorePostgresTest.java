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
            "/db/migration/V7__workspace_domain_invariants.sql");

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
    void rejectsAuditMetadataThatDoesNotDescribeTheMutation() {
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
    void rejectsPageAuditActorAndRevisionThatDifferFromTheSnapshot() {
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
        AuditEntry invalidAudit = new AuditEntry(tenant, actor, "A".repeat(101), "WORKSPACE",
                UUID.randomUUID(), 0, "force audit write failure", now);

        Workspace workspace = Workspace.create(tenant, "Atomic workspace", "", now);
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
            // These migration files contain ordinary DDL statements only; semicolon splitting is sufficient.
            try (Connection c = source.getConnection(); Statement s = c.createStatement()) {
                for (String statement : sql.split(";")) {
                    String executable = statement.lines().map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                            .reduce("", (left, right) -> left + " " + right).trim();
                    if (!executable.isEmpty()) s.execute(executable);
                }
            }
        }
    }
}
