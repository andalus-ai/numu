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
            "/db/migration/V4__workspace_page_search_indexes.sql");

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
