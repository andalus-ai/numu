package tech.kayys.numou.omnichannel.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.omnichannel.core.InboundWorkItem;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL integration tests. Set NUMOU_TEST_POSTGRES_URL (and optionally
 * NUMOU_TEST_POSTGRES_USER / NUMOU_TEST_POSTGRES_PASSWORD) to enable them.
 * The configured database should be disposable: the tests apply idempotent
 * module migrations and remove only rows bearing their generated tenant IDs.
 */
class PostgresInboundEventReliabilityTest {
    private static final String URL_ENV = "NUMOU_TEST_POSTGRES_URL";
    private final String tenant = UUID.randomUUID().toString();
    private DataSource dataSource;
    private PostgresInboundEventProcessingStore processingStore;
    private PostgresInboundEventRecoveryStore recoveryStore;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv(URL_ENV);
        assumeTrue(url != null && !url.isBlank(),
                "Set " + URL_ENV + " to enable PostgreSQL integration tests");

        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL(url);
        String user = System.getenv("NUMOU_TEST_POSTGRES_USER");
        String password = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
        if (user != null) pg.setUser(user);
        if (password != null) pg.setPassword(password);
        dataSource = pg;
        applyMigration("db/migration/V1__create_omnichannel_inbound_event.sql");
        applyMigration("db/migration/V2__add_inbound_event_retry_lifecycle.sql");
        applyMigration("db/migration/V3__persist_verified_payload_reference.sql");
        processingStore = new PostgresInboundEventProcessingStore(dataSource);
        recoveryStore = new PostgresInboundEventRecoveryStore(dataSource);
    }

    @Test
    void concurrentClaimsNeverLeaseTheSameVerifiedEventTwice() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        insertEvent("concurrent-event", "VERIFIED", now.minusSeconds(30), true, 0);

        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Optional<InboundWorkItem>> claim = () ->
                    processingStore.claimNext(now, now.plusSeconds(30), 5);
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            Optional<InboundWorkItem> a = first.get(10, TimeUnit.SECONDS);
            Optional<InboundWorkItem> b = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(a.isPresent(), b.isPresent()).stream().filter(Boolean::booleanValue).count())
                    .isEqualTo(1);
            assertThat(a.orElseGet(() -> b.orElseThrow()).leaseToken()).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldLeaseCannotAcknowledge() {
        Instant firstNow = Instant.parse("2026-01-01T00:00:00Z");
        insertEvent("lease-event", "VERIFIED", firstNow.minusSeconds(30), true, 0);

        InboundWorkItem original = processingStore.claimNext(
                firstNow, firstNow.plusSeconds(10), 5).orElseThrow();
        InboundWorkItem reclaimed = processingStore.claimNext(
                firstNow.plusSeconds(11), firstNow.plusSeconds(41), 5).orElseThrow();

        assertThat(reclaimed.leaseToken()).isNotEqualTo(original.leaseToken());
        assertThat(processingStore.markProcessed(original, firstNow.plusSeconds(12))).isFalse();
        assertThat(processingStore.markProcessed(reclaimed, firstNow.plusSeconds(12))).isTrue();
    }

    @Test
    void staleReceivedWithoutPayloadIsQuarantinedButFreshRowIsNot() throws SQLException {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        insertEvent("stale-ingestion", "RECEIVED", now.minusSeconds(3600), false, 0);
        insertEvent("fresh-ingestion", "RECEIVED", now.minusSeconds(5), false, 0);

        int quarantined = recoveryStore.quarantineStaleReceived(
                now.minusSeconds(300), now, 100);

        assertThat(quarantined).isEqualTo(1);
        assertThat(stateOf("stale-ingestion")).isEqualTo("DEAD_LETTER");
        assertThat(errorCodeOf("stale-ingestion")).isEqualTo("INGESTION_INCOMPLETE");
        assertThat(stateOf("fresh-ingestion")).isEqualTo("RECEIVED");
    }

    private void insertEvent(String eventId, String state, Instant receivedAt,
                             boolean withPayloadReference, int attemptCount) {
        String sql = """
                INSERT INTO numou_omnichannel_inbound_event (
                    tenant_id, provider_id, provider_account_id, external_event_id,
                    event_type, payload_sha256, channel_type, branch_id, correlation_id,
                    received_at, state, attempt_count, updated_at, payload_reference, verified_at
                ) VALUES (?, 'test-provider', 'test-account', ?, 'order.updated', ?, 'MARKETPLACE',
                          NULL, 'integration-test', ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenant);
            statement.setString(2, eventId);
            statement.setString(3, "a".repeat(64));
            statement.setObject(4, receivedAt.atOffset(java.time.ZoneOffset.UTC));
            statement.setString(5, state);
            statement.setInt(6, attemptCount);
            statement.setObject(7, receivedAt.atOffset(java.time.ZoneOffset.UTC));
            statement.setString(8, withPayloadReference ? "opaque-ref-" + eventId : null);
            statement.setObject(9, withPayloadReference
                    ? receivedAt.plusSeconds(1).atOffset(java.time.ZoneOffset.UTC) : null);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to seed PostgreSQL integration row", exception);
        }
    }

    private String stateOf(String eventId) throws SQLException {
        return selectColumn(eventId, "state");
    }

    private String errorCodeOf(String eventId) throws SQLException {
        return selectColumn(eventId, "last_error_code");
    }

    private String selectColumn(String eventId, String column) throws SQLException {
        // Column names are constants owned by this test, never caller input.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + column + " FROM numou_omnichannel_inbound_event "
                             + "WHERE tenant_id = ? AND external_event_id = ?")) {
            statement.setString(1, tenant);
            statement.setString(2, eventId);
            try (var rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private void applyMigration(String resource) throws IOException, SQLException {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource: " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }
}
