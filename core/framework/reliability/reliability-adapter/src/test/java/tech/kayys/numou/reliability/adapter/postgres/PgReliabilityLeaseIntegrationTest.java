package tech.kayys.numou.reliability.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the production PostgreSQL claim/update statements against real PostgreSQL.
 * These tests intentionally use independent connections to model competing workers.
 */
@Testcontainers(disabledWithoutDocker = true)
class PgReliabilityLeaseIntegrationTest {
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
    void upgradesAnExistingV100InstallationThroughV102() throws Exception {
        flyway().clean();
        flyway().target(MigrationVersion.fromVersion("100")).migrate();

        try (Connection connection = connection()) {
            assertThat(hasColumn(connection, "platform_inbox", "processing_token")).isFalse();
            assertThat(hasColumn(connection, "platform_outbox", "lease_token")).isFalse();
        }

        flyway().migrate();

        try (Connection connection = connection()) {
            assertThat(hasColumn(connection, "platform_inbox", "processing_token")).isTrue();
            assertThat(hasColumn(connection, "platform_inbox", "locked_until")).isTrue();
            assertThat(hasColumn(connection, "platform_outbox", "lease_token")).isTrue();
        }
    }

    @Test
    void onlyOneOfTwoIndependentInboxWorkersCanAcquireTheSameEvent() throws Exception {
        final String eventId = "evt-" + UUID.randomUUID();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO platform_inbox(entry_id,event_id,event_type,received_at) "
                    + "VALUES ('" + UUID.randomUUID() + "','" + eventId + "','sample',clock_timestamp())");
        }

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = workers.submit(() -> claimInboxAfterBarrier(eventId, UUID.randomUUID(), ready, start));
            Future<Boolean> second = workers.submit(() -> claimInboxAfterBarrier(eventId, UUID.randomUUID(), ready, start));
            ready.await();
            start.countDown();
            assertThat((int) (first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        }
    }

    @Test
    void expiredInboxLeaseCanBeTakenOverAndStaleWorkerCannotComplete() throws Exception {
        final String eventId = "evt-" + UUID.randomUUID();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO platform_inbox(entry_id,event_id,event_type,received_at) "
                    + "VALUES ('" + UUID.randomUUID() + "','" + eventId + "','sample',clock_timestamp())");
        }

        UUID firstToken = UUID.randomUUID();
        assertThat(claimInbox(eventId, firstToken, Instant.now(), Instant.now().plusMillis(250))).isTrue();
        Thread.sleep(400);

        UUID secondToken = UUID.randomUUID();
        assertThat(claimInbox(eventId, secondToken, Instant.now(), Instant.now().plusSeconds(10))).isTrue();
        assertThat(markInboxHandled(eventId, firstToken)).isZero();
        assertThat(markInboxHandled(eventId, secondToken)).isEqualTo(1);
    }

    @Test
    void onlyOneOfTwoIndependentOutboxWorkersCanClaimTheSameEvent() throws Exception {
        UUID entryId = insertOutboxEntry();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = workers.submit(() -> claimOutboxAfterBarrier(ready, start));
            Future<UUID> second = workers.submit(() -> claimOutboxAfterBarrier(ready, start));
            ready.await();
            start.countDown();
            UUID firstToken = first.get();
            UUID secondToken = second.get();
            assertThat((firstToken == null ? 0 : 1) + (secondToken == null ? 0 : 1)).isEqualTo(1);
            assertThat(firstToken == null ? secondToken : firstToken).isNotNull();
        }
    }

    @Test
    void expiredOutboxLeaseRejectsStaleUpdateAfterTakeover() throws Exception {
        UUID entryId = insertOutboxEntry();
        UUID firstToken = claimOutbox(250);
        assertThat(firstToken).isNotNull();
        Thread.sleep(400);

        UUID secondToken = claimOutbox(10_000);
        assertThat(secondToken).isNotNull();
        assertThat(updateOutbox(entryId, firstToken)).isZero();
        assertThat(updateOutbox(entryId, secondToken)).isEqualTo(1);
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

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, "public", table, column)) {
            return rs.next();
        }
    }

    private boolean claimInboxAfterBarrier(String eventId, UUID token, CountDownLatch ready,
                                           CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        Instant now = Instant.now();
        return claimInbox(eventId, token, now, now.plusSeconds(30));
    }

    private boolean claimInbox(String eventId, UUID token, Instant now, Instant leaseUntil) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgInboxStore.CLAIM))) {
            statement.setString(1, eventId);
            statement.setObject(2, token);
            statement.setObject(3, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            statement.setObject(4, OffsetDateTime.ofInstant(leaseUntil, ZoneOffset.UTC));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int markInboxHandled(String eventId, UUID token) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgInboxStore.MARK))) {
            statement.setString(1, eventId);
            statement.setObject(2, token);
            return statement.executeUpdate();
        }
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

    private UUID claimOutboxAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return claimOutbox(30_000);
    }

    private UUID claimOutbox(long leaseMillis) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgOutboxStore.CLAIM))) {
            statement.setInt(1, 1);
            statement.setDouble(2, leaseMillis);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getObject("lease_token", UUID.class) : null;
            }
        }
    }

    private int updateOutbox(UUID entryId, UUID token) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(jdbc(PgOutboxStore.UPDATE))) {
            statement.setObject(1, entryId);
            statement.setObject(2, token);
            statement.setInt(3, 0);
            statement.setObject(4, OffsetDateTime.now(ZoneOffset.UTC));
            statement.setNull(5, Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setNull(6, Types.VARCHAR);
            return statement.executeUpdate();
        }
    }

    /** Translate the adapter's Vert.x positional placeholders to JDBC placeholders. */
    private static String jdbc(String sql) {
        return sql.replaceAll("\\$[1-9][0-9]*", "?");
    }
}
