package tech.kayys.payment.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPaymentOutboxRelayStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_payment_outbox")
            .withUsername("numou")
            .withPassword("numou");

    private DataSource dataSource;
    private PostgresPaymentOutboxRelayStore store;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        store = new PostgresPaymentOutboxRelayStore(dataSource);
        applyMigration("/db/migration/V1__payment_obligations_and_outbox.sql");
        applyMigration("/db/migration/V2__payment_outbox_leases_and_dead_letters.sql");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment_operation_outbox");
        }
    }

    @AfterEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE payment_operation_outbox");
        }
    }

    @Test
    void claimAndPublishIsFencedByLeaseToken() throws Exception {
        UUID eventId = seedEvent();
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        var first = store.claimBatch("worker-a", now, now.plusSeconds(10), 10).getFirst();
        assertThat(first.eventId()).isEqualTo(eventId);
        assertThat(first.attempts()).isEqualTo(1);

        var reclaimed = store.claimBatch("worker-b", now.plusSeconds(11), now.plusSeconds(30), 10).getFirst();
        assertThat(reclaimed.leaseToken()).isNotEqualTo(first.leaseToken());
        assertThat(reclaimed.attempts()).isEqualTo(2);
        assertThat(store.markPublished(eventId, first.leaseToken(), now.plusSeconds(12))).isFalse();
        assertThat(store.markPublished(eventId, reclaimed.leaseToken(), now.plusSeconds(13))).isTrue();
        assertThat(store.claimBatch("worker-c", now.plusSeconds(14), now.plusSeconds(40), 10)).isEmpty();
        assertThat(outboxValue(eventId, "published_at")).isNotNull();
    }

    @Test
    void competingWorkersCannotClaimSameLiveLease() throws Exception {
        seedEvent();
        var pool = Executors.newFixedThreadPool(2);
        try {
            Instant now = Instant.parse("2026-10-28T00:00:00Z");
            var first = CompletableFuture.supplyAsync(
                    () -> store.claimBatch("worker-a", now, now.plusSeconds(30), 10), pool);
            var second = CompletableFuture.supplyAsync(
                    () -> store.claimBatch("worker-b", now, now.plusSeconds(30), 10), pool);
            List<PaymentOutboxRelayStore.ClaimedEvent> a = first.get(10, TimeUnit.SECONDS);
            List<PaymentOutboxRelayStore.ClaimedEvent> b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.size() + b.size()).isEqualTo(1);
            assertThat(a.stream().map(PaymentOutboxRelayStore.ClaimedEvent::eventId)
                    .toList()).doesNotContainAnyElementsOf(
                            b.stream().map(PaymentOutboxRelayStore.ClaimedEvent::eventId).toList());
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void abandonedFinalLeaseBecomesTerminalInsteadOfBeingReclaimedForever() throws Exception {
        UUID eventId = seedEvent();
        Instant base = Instant.parse("2026-10-28T00:00:00Z");
        for (int attempt = 0; attempt < 12; attempt++) {
            Instant now = base.plusSeconds(attempt * 2L);
            var claimed = store.claimBatch("worker-" + attempt, now, now.plusSeconds(1), 1).getFirst();
            assertThat(claimed.attempts()).isEqualTo(attempt + 1);
        }

        Instant afterLastLease = base.plusSeconds(24);
        assertThat(store.claimBatch("recovery-worker", afterLastLease, afterLastLease.plusSeconds(30), 1)).isEmpty();
        assertThat(outboxValue(eventId, "terminal")).isEqualTo("true");
    }

    @Test
    void failedEventsAreDelayedOrMovedToTerminalState() throws Exception {
        UUID eventId = seedEvent();
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        var event = store.claimBatch("worker-a", now, now.plusSeconds(30), 10).getFirst();
        assertThat(store.markFailed(eventId, event.leaseToken(), now.plusSeconds(60), "temporary", false)).isTrue();
        assertThat(store.claimBatch("worker-b", now.plusSeconds(30), now.plusSeconds(90), 10)).isEmpty();

        var retry = store.claimBatch("worker-b", now.plusSeconds(61), now.plusSeconds(90), 10).getFirst();
        assertThat(store.markFailed(eventId, retry.leaseToken(), now.plusSeconds(61), "permanent", true)).isTrue();
        assertThat(store.claimBatch("worker-c", now.plusSeconds(62), now.plusSeconds(120), 10)).isEmpty();
        assertThat(outboxValue(eventId, "terminal")).isEqualTo("true");
    }

    private UUID seedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO payment_operation_outbox
                         (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                     VALUES (?, 'tenant-test', 'PAYMENT', ?, 'PAYMENT_STATUS_CHANGED', CAST(? AS jsonb), ?)
                     """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, aggregateId);
            statement.setString(3, "{\"paymentId\":\"" + aggregateId + "\"}");
            statement.setObject(4, java.time.OffsetDateTime.ofInstant(
                    Instant.parse("2026-10-28T00:00:00Z"), java.time.ZoneOffset.UTC));
            statement.executeUpdate();
        }
        return eventId;
    }

    private String outboxValue(UUID eventId, String column) throws Exception {
        if (!List.of("published_at", "terminal").contains(column)) {
            throw new IllegalArgumentException("Unsupported test column");
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT " + column + " FROM payment_operation_outbox WHERE event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                Object value = result.getObject(1);
                return value == null ? null : value.toString();
            }
        }
    }

    private void applyMigration(String resource) throws Exception {
        try (InputStream migration = getClass().getResourceAsStream(resource)) {
            if (migration == null) throw new IllegalStateException("Missing migration " + resource);
            String sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }
}
