package tech.kayys.numou.reliability.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies persisted failure diagnostics and lease fencing against PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
class PgOutboxFailureDiagnosticPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_reliability")
            .withUsername("numou")
            .withPassword("numou");

    @BeforeEach
    void migrateLatestSchema() {
        flyway().clean();
        flyway().migrate();
    }

    @Test
    void retryDiagnosticSurvivesAndStaleWorkersCannotOverwriteItThenSuccessClearsIt() throws Exception {
        UUID entryId = insertOutboxEntry();
        UUID firstToken = claimOutbox();
        assertThat(firstToken).isNotNull();

        String firstError = "IllegalStateException: broker unavailable";
        assertThat(updateOutbox(entryId, firstToken, 1, Instant.now().plusSeconds(5), null, firstError)).isEqualTo(1);
        assertThat(lastError(entryId)).isEqualTo(firstError);

        makeDue(entryId);
        UUID secondToken = claimOutbox();
        assertThat(secondToken).isNotNull().isNotEqualTo(firstToken);

        String secondError = "TimeoutException: broker timed out";
        assertThat(updateOutbox(entryId, secondToken, 2, Instant.now().plusSeconds(10), null, secondError)).isEqualTo(1);
        assertThat(updateOutbox(entryId, firstToken, 3, Instant.now().plusSeconds(15), null, "stale first worker")).isZero();
        assertThat(lastError(entryId)).isEqualTo(secondError);

        makeDue(entryId);
        UUID thirdToken = claimOutbox();
        assertThat(thirdToken).isNotNull().isNotEqualTo(secondToken);
        assertThat(updateOutbox(entryId, secondToken, 3, Instant.now().plusSeconds(20), null, "stale second worker")).isZero();
        assertThat(lastError(entryId)).isEqualTo(secondError);

        assertThat(updateOutbox(entryId, thirdToken, 2, Instant.now(), Instant.now(), null)).isEqualTo(1);
        assertThat(lastError(entryId)).isNull();
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private UUID insertOutboxEntry() throws SQLException {
        UUID entryId = UUID.randomUUID();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO platform_outbox(entry_id,event_id,event_type,payload,created_at,available_at)
                     VALUES (?,?,?,'{}'::jsonb,clock_timestamp(),clock_timestamp())
                     """)) {
            statement.setObject(1, entryId);
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, "sample.event");
            statement.executeUpdate();
        }
        return entryId;
    }

    private void makeDue(UUID entryId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE platform_outbox
                        SET available_at = clock_timestamp(),
                            locked_until = clock_timestamp() - interval '1 second'
                      WHERE entry_id = ?
                     """)) {
            statement.setObject(1, entryId);
            statement.executeUpdate();
        }
    }

    private UUID claimOutbox() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgOutboxStore.CLAIM))) {
            statement.setInt(1, 1);
            statement.setDouble(2, 30_000);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getObject("lease_token", UUID.class) : null;
            }
        }
    }

    private int updateOutbox(UUID entryId, UUID token, int attempts, Instant availableAt,
                             Instant publishedAt, String lastError) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgOutboxStore.UPDATE))) {
            statement.setObject(1, entryId);
            statement.setObject(2, token);
            statement.setInt(3, attempts);
            statement.setObject(4, OffsetDateTime.ofInstant(availableAt, ZoneOffset.UTC));
            if (publishedAt == null) statement.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            else statement.setObject(5, OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC));
            if (lastError == null) statement.setNull(6, Types.VARCHAR);
            else statement.setString(6, lastError);
            return statement.executeUpdate();
        }
    }

    private String lastError(UUID entryId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT last_error FROM platform_outbox WHERE entry_id = ?")) {
            statement.setObject(1, entryId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static String jdbc(String sql) {
        return sql.replaceAll("\\$[1-9][0-9]*", "?");
    }
}