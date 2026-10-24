package tech.kayys.numou.omnichannel.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.omnichannel.core.ChannelContext;
import tech.kayys.numou.omnichannel.core.ChannelType;
import tech.kayys.numou.omnichannel.core.ExponentialBackoffRetryPolicy;
import tech.kayys.numou.omnichannel.core.InboundEventDispatcher;
import tech.kayys.numou.omnichannel.core.InboundEventWorker;
import tech.kayys.numou.omnichannel.core.InboundPayloadReference;
import tech.kayys.numou.omnichannel.core.InboundPayloadStore;
import tech.kayys.numou.omnichannel.core.InboundWorkerDisposition;
import tech.kayys.numou.omnichannel.core.VerifiedInboundEvent;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end lifecycle tests against real PostgreSQL persistence. Set
 * NUMOU_TEST_POSTGRES_URL (and optionally NUMOU_TEST_POSTGRES_USER /
 * NUMOU_TEST_POSTGRES_PASSWORD) to enable these tests.
 */
class PostgresInboundEventWorkerLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");
    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = sha256(BODY);
    private static final String EVENT_ID = "worker-lifecycle-event";

    private final String tenant = UUID.randomUUID().toString();
    private final String schema = "omni_worker_" + UUID.randomUUID().toString().replace("-", "");
    private PGSimpleDataSource rootDataSource;
    private DataSource dataSource;
    private PostgresInboundEventProcessingStore processingStore;
    private PostgresInboundVerifiedEventMetadataStore metadataStore;

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
        rootDataSource = pg;
        try (Connection connection = pg.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        pg.setCurrentSchema(schema);
        dataSource = pg;
        applyMigration("db/migration/V1__create_omnichannel_inbound_event.sql");
        applyMigration("db/migration/V2__add_inbound_event_retry_lifecycle.sql");
        applyMigration("db/migration/V3__persist_verified_payload_reference.sql");
        processingStore = new PostgresInboundEventProcessingStore(dataSource);
        metadataStore = new PostgresInboundVerifiedEventMetadataStore(dataSource);
    }

    @AfterEach
    void dropIsolatedSchema() throws SQLException {
        if (rootDataSource == null) return;
        try (Connection connection = rootDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void dispatcherFailureIsDurablyRetriedThenSuccessfulAttemptIsAcknowledged() throws Exception {
        insertVerifiedEvent();

        InboundEventWorker failingWorker = worker(Clock.fixed(NOW, ZoneOffset.UTC), event -> {
            throw new IllegalStateException("sensitive payload must not be persisted");
        });
        assertThat(failingWorker.processNext()).isEqualTo(InboundWorkerDisposition.RETRY_SCHEDULED);
        assertThat(stateOf("state")).isEqualTo("RETRYABLE_FAILURE");
        assertThat(stateOf("attempt_count")).isEqualTo(1);
        assertThat(stateOf("last_error_code")).isEqualTo("INBOUND_DISPATCH_FAILED");
        assertThat(stateOf("lease_token")).isNull();
        assertThat(stateOf("next_attempt_at")).isNotNull();

        AtomicReference<VerifiedInboundEvent> delivered = new AtomicReference<>();
        InboundEventWorker retryWorker = worker(
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC), delivered::set);

        assertThat(retryWorker.processNext()).isEqualTo(InboundWorkerDisposition.PROCESSED);
        assertThat(delivered.get()).isNotNull();
        assertThat(delivered.get().envelope().externalEventId()).isEqualTo(EVENT_ID);
        assertThat(delivered.get().idempotencyKey()).isNotBlank();
        assertThat(stateOf("state")).isEqualTo("PROCESSED");
        assertThat(stateOf("attempt_count")).isEqualTo(2);
        assertThat(stateOf("last_error_code")).isNull();
        assertThat(stateOf("lease_token")).isNull();
        assertThat(stateOf("processed_at")).isNotNull();
    }

    @Test
    void leaseReclaimedDuringDispatchFencesTheOldWorkersAcknowledgement() throws Exception {
        insertVerifiedEvent();
        AtomicReference<String> replacementToken = new AtomicReference<>();

        InboundEventWorker worker = worker(Clock.fixed(NOW, ZoneOffset.UTC), event -> {
            var replacement = processingStore.claimNext(
                    NOW.plusSeconds(11), NOW.plusSeconds(41), 5).orElseThrow();
            replacementToken.set(replacement.leaseToken().orElseThrow());
        }, Duration.ofSeconds(10));

        assertThat(worker.processNext()).isEqualTo(InboundWorkerDisposition.STALE_LEASE);
        assertThat(replacementToken.get()).isNotBlank();
        assertThat(stateOf("state")).isEqualTo("PROCESSING");
        assertThat(stateOf("lease_token")).isEqualTo(replacementToken.get());
        assertThat(stateOf("processed_at")).isNull();
    }

    private InboundEventWorker worker(Clock clock, InboundEventDispatcher dispatcher) {
        return worker(clock, dispatcher, Duration.ofSeconds(30));
    }

    private InboundEventWorker worker(Clock clock, InboundEventDispatcher dispatcher, Duration leaseDuration) {
        return new InboundEventWorker(processingStore, metadataStore, payloadStore(), dispatcher,
                clock, leaseDuration,
                new ExponentialBackoffRetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 0.0), 5);
    }

    private InboundPayloadStore payloadStore() {
        return new InboundPayloadStore() {
            @Override
            public InboundPayloadReference store(ChannelContext context, byte[] payload,
                    String expectedSha256, Instant retentionUntil) {
                throw new UnsupportedOperationException("This test only loads previously verified payloads");
            }

            @Override
            public Optional<byte[]> load(ChannelContext context, InboundPayloadReference reference) {
                if (!"opaque-ref-" .concat(EVENT_ID).equals(reference.value())) return Optional.empty();
                if (!context.tenantId().equals(TenantId.of(tenant))) return Optional.empty();
                return Optional.of(BODY.clone());
            }

            @Override
            public boolean delete(ChannelContext context, InboundPayloadReference reference) {
                return false;
            }
        };
    }

    private void insertVerifiedEvent() throws SQLException {
        String sql = """
                INSERT INTO numou_omnichannel_inbound_event (
                    tenant_id, provider_id, provider_account_id, external_event_id,
                    event_type, payload_sha256, channel_type, branch_id, correlation_id,
                    received_at, state, attempt_count, updated_at, payload_reference, verified_at
                ) VALUES (?, 'test-provider', 'test-account', ?, 'order.updated', ?, 'MARKETPLACE',
                          NULL, 'integration-test', ?, 'VERIFIED', 0, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenant);
            statement.setString(2, EVENT_ID);
            statement.setString(3, DIGEST);
            statement.setObject(4, NOW.minusSeconds(30).atOffset(ZoneOffset.UTC));
            statement.setObject(5, NOW.minusSeconds(30).atOffset(ZoneOffset.UTC));
            statement.setString(6, "opaque-ref-" + EVENT_ID);
            statement.setObject(7, NOW.minusSeconds(29).atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private Object stateOf(String column) throws SQLException {
        // Column is a constant selected by these tests, never external input.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + column + " FROM numou_omnichannel_inbound_event "
                             + "WHERE tenant_id = ? AND external_event_id = ?")) {
            statement.setString(1, tenant);
            statement.setString(2, EVENT_ID);
            try (var rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                Object value = rs.getObject(1);
                if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
                return value;
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
