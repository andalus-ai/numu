package tech.kayys.numou.omnichannel.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.omnichannel.core.DeadLetterQuery;
import tech.kayys.numou.omnichannel.core.DeadLetterReplayResult;
import tech.kayys.numou.omnichannel.core.InboundEventKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in integration tests; uses an isolated schema in a disposable PostgreSQL database. */
class PostgresDeadLetterOperationsTest {
    private final String tenant = UUID.randomUUID().toString();
    private final String otherTenant = UUID.randomUUID().toString();
    private final String schema = "omni_dl_test_" + UUID.randomUUID().toString().replace("-", "");
    private PGSimpleDataSource root;
    private PGSimpleDataSource dataSource;
    private PostgresDeadLetterOperations operations;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Set NUMOU_TEST_POSTGRES_URL to enable PostgreSQL integration tests");
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL(url);
        String user = System.getenv("NUMOU_TEST_POSTGRES_USER");
        String password = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
        if (user != null) pg.setUser(user);
        if (password != null) pg.setPassword(password);
        root = pg;
        try (Connection connection = pg.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        pg.setCurrentSchema(schema);
        dataSource = pg;
        applyMigration("db/migration/V1__create_omnichannel_inbound_event.sql");
        applyMigration("db/migration/V2__add_inbound_event_retry_lifecycle.sql");
        applyMigration("db/migration/V3__persist_verified_payload_reference.sql");
        applyMigration("db/migration/V4__audit_dead_letter_replay.sql");
        operations = new PostgresDeadLetterOperations(dataSource);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        if (root == null) return;
        try (Connection connection = root.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void listingIsTenantScopedAndFiltersSanitizedErrorCode() {
        insertDeadLetter(tenant, "evt-a", "RETRY_EXHAUSTED", 5);
        insertDeadLetter(otherTenant, "evt-b", "RETRY_EXHAUSTED", 8);
        insertDeadLetter(tenant, "evt-c", "PAYLOAD_INVALID", 4);

        var results = operations.find(new DeadLetterQuery(
                TenantId.of(tenant), Optional.of("provider-test"), Optional.of("RETRY_EXHAUSTED"),
                Optional.empty(), 3, 50));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().key().tenantId().toString()).isEqualTo(tenant);
        assertThat(results.getFirst().key().externalEventId()).isEqualTo("evt-a");
    }

    @Test
    void concurrentReplayQueuesExactlyOnceAndWritesOneAuditRecord() throws Exception {
        insertDeadLetter(tenant, "evt-replay", "RETRY_EXHAUSTED", 5);
        InboundEventKey key = new InboundEventKey(
                TenantId.of(tenant), "provider-test", "account-test", "evt-replay");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> operations.replay(
                    key, "operator-a", "approved after review", Instant.parse("2026-10-24T00:00:00Z")));
            var second = pool.submit(() -> operations.replay(
                    key, "operator-b", "approved after review", Instant.parse("2026-10-24T00:00:01Z")));
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(
                    DeadLetterReplayResult.REQUEUED, DeadLetterReplayResult.NOT_FOUND_OR_NOT_DEAD_LETTER);
            assertThat(stateOf("evt-replay")).isEqualTo("RETRYABLE_FAILURE");
            assertThat(auditCount("evt-replay")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private void insertDeadLetter(String tenantId, String eventId, String errorCode, int attempts) {
        String sql = """
                INSERT INTO numou_omnichannel_inbound_event (
                    tenant_id, provider_id, provider_account_id, external_event_id,
                    event_type, payload_sha256, channel_type, correlation_id,
                    received_at, state, attempt_count, last_error_code, updated_at
                ) VALUES (?, 'provider-test', 'account-test', ?, 'order.updated', ?, 'MARKETPLACE',
                          'dead-letter-test', ?, 'DEAD_LETTER', ?, ?, ?)
                """;
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setString(2, eventId);
            statement.setString(3, "b".repeat(64));
            statement.setObject(4, now.atOffset(java.time.ZoneOffset.UTC));
            statement.setInt(5, attempts);
            statement.setString(6, errorCode);
            statement.setObject(7, now.atOffset(java.time.ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to seed dead-letter integration row", exception);
        }
    }

    private String stateOf(String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM numou_omnichannel_inbound_event WHERE tenant_id = ? AND external_event_id = ?")) {
            statement.setString(1, tenant);
            statement.setString(2, eventId);
            try (var rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private int auditCount(String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM numou_omnichannel_dead_letter_audit WHERE tenant_id = ? AND external_event_id = ?")) {
            statement.setString(1, tenant);
            statement.setString(2, eventId);
            try (var rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1);
            }
        }
    }

    private void applyMigration(String resource) throws IOException, SQLException {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource: " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
            }
        }
    }
}
