package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in PostgreSQL verification using the workspace disposable test database settings. */
class JdbcWorkspaceMentionOutboxHealthStorePostgresTest {
    private String jdbcUrl;
    private String schema;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = System.getenv("NUMOU_WORKSPACE_TEST_JDBC_URL");
        Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
                "PostgreSQL integration tests require NUMOU_WORKSPACE_TEST_JDBC_URL");
        String user = System.getenv("NUMOU_WORKSPACE_TEST_DB_USER");
        String password = System.getenv("NUMOU_WORKSPACE_TEST_DB_PASSWORD");
        schema = "workspace_mention_health_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(jdbcUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        if (user != null && !user.isBlank()) postgres.setUser(user);
        if (password != null) postgres.setPassword(password);
        dataSource = postgres;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE workspace_comment_mention_outbox (
                      id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
                      status VARCHAR(16) NOT NULL, available_at TIMESTAMPTZ NOT NULL,
                      lease_until TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null || jdbcUrl == null) return;
        String user = System.getenv("NUMOU_WORKSPACE_TEST_DB_USER");
        String password = System.getenv("NUMOU_WORKSPACE_TEST_DB_PASSWORD");
        try (Connection connection = connect(jdbcUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void returnsOnlyTenantAggregateAndZeroCountersForEmptyTenant() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO workspace_comment_mention_outbox VALUES " +
                    "(gen_random_uuid(),'" + tenant + "','PENDING','2026-11-01T23:00:00Z',NULL,'2026-11-01T22:00:00Z')," +
                    "(gen_random_uuid(),'" + tenant + "','LEASED','2026-11-01T23:00:00Z','2026-11-01T23:30:00Z','2026-11-01T22:30:00Z')," +
                    "(gen_random_uuid(),'" + tenant + "','DEAD','2026-11-01T23:00:00Z',NULL,'2026-11-01T21:00:00Z')," +
                    "(gen_random_uuid(),'" + otherTenant + "','PENDING','2026-11-01T23:00:00Z',NULL,'2026-11-01T20:00:00Z')");
        }

        var store = new JdbcWorkspaceMentionOutboxHealthStore(dataSource);
        var result = store.snapshot(tenant, now);
        var empty = store.snapshot(UUID.randomUUID(), now);

        assertEquals(1, result.pending());
        assertEquals(1, result.due());
        assertEquals(1, result.leased());
        assertEquals(1, result.expiredLeases());
        assertEquals(1, result.dead());
        assertEquals(Instant.parse("2026-11-01T22:00:00Z"), result.oldestPendingAt());
        assertEquals(0, empty.pending());
        assertEquals(0, empty.due());
        assertEquals(0, empty.leased());
        assertEquals(0, empty.expiredLeases());
        assertEquals(0, empty.dead());
        assertNull(empty.oldestPendingAt());
    }

    private static Connection connect(String url, String user, String password) throws Exception {
        if (user == null || user.isBlank()) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }
}
