package tech.kayys.numou.reliability.adapter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.adapter.json.JsonEventSerializer;
import tech.kayys.numou.reliability.outbox.OutboxClaim;
import tech.kayys.numou.reliability.outbox.OutboxClaim;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgOutboxStorePoisonRowPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static Vertx vertx;
    private static PgPool pool;
    private static JsonEventSerializer serializer;
    private static PgOutboxStore store;

    @BeforeAll
    static void startStoreAndApplyCanonicalMigrations() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            executeMigration(statement, "db/migration/V100__platform_reliability.sql");
            executeMigration(statement, "db/migration/V101__platform_inbox_processing_lease.sql");
            executeMigration(statement, "db/migration/V102__platform_outbox_lease_fencing.sql");
        }

        vertx = Vertx.vertx();
        PgConnectOptions connect = new PgConnectOptions()
                .setHost(POSTGRES.getHost())
                .setPort(POSTGRES.getMappedPort(5432))
                .setDatabase(POSTGRES.getDatabaseName())
                .setUser(POSTGRES.getUsername())
                .setPassword(POSTGRES.getPassword());
        pool = PgPool.pool(vertx, connect, new PoolOptions().setMaxSize(4));
        serializer = new JsonEventSerializer(new ObjectMapper(),
                new EventTypeRegistry().registerFallback(JsonDomainEvent.class));
        store = new PgOutboxStore();
        store.pool = pool;
        store.serializer = serializer;
        store.lease = Duration.ofSeconds(30);
    }

    @AfterAll
    static void closeStore() {
        if (pool != null) pool.close().await().indefinitely();
        if (vertx != null) vertx.closeAndAwait();
    }

    @BeforeEach
    void clearOutbox() throws Exception {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE platform_outbox");
        }
    }

    @Test
    void malformedClaimIsQuarantinedWithoutBlockingValidGenericEventInSameBatch() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID validEntryId = UUID.randomUUID();
        UUID poisonEntryId = UUID.randomUUID();
        String eventType = "organization.party-association.changed";
        Instant now = Instant.now();

        var data = new ObjectMapper().createObjectNode();
        data.put("associationId", UUID.randomUUID().toString());
        data.put("transition", "PENDING_VERIFICATION_TO_ACTIVE");
        var payload = new JsonDomainEvent(UUID.randomUUID(), now.toString(), eventType, data);
        var metadata = EventMetadata.of(tenantId, null, "integration-test", CorrelationId.of("m1f-poison-test"));
        var envelope = EventEnvelope.of(metadata, payload);
        insert(validEntryId, metadata.eventId().value(), tenantId, eventType,
                serializer.serialize(envelope), now.minusSeconds(2));
        insert(poisonEntryId, UUID.randomUUID(), tenantId, eventType,
                "{\"specVersion\":\"1\"}", now.minusSeconds(1));

        var claims = store.nextDue(10).await().indefinitely();

        assertThat(claims).hasSize(1);
        OutboxClaim validClaim = claims.get(0);
        assertThat(validClaim.entry().entryId()).isEqualTo(validEntryId);
        assertThat(validClaim.entry().envelope().eventType()).isEqualTo(eventType);

        try (Connection connection = dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT attempts, locked_until, lease_token, last_error
                       FROM platform_outbox WHERE entry_id = ?
                     """)) {
            statement.setObject(1, poisonEntryId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("attempts")).isEqualTo(10);
                assertThat(rows.getTimestamp("locked_until")).isNull();
                assertThat(rows.getObject("lease_token")).isNull();
                assertThat(rows.getString("last_error")).isNotBlank();
                assertThat(rows.getString("last_error").length()).isLessThanOrEqualTo(2000);
            }
        }

        assertThat(store.countDeadLetters().await().indefinitely()).isEqualTo(1L);
        assertThat(store.update(validClaim, validClaim.entry().markPublished()).await().indefinitely()).isTrue();

        try (Connection connection = dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     SELECT published_at, attempts, locked_until
                       FROM platform_outbox WHERE entry_id = ?
                     """)) {
            statement.setObject(1, validEntryId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getTimestamp("published_at")).isNotNull();
                assertThat(rows.getInt("attempts")).isZero();
                assertThat(rows.getTimestamp("locked_until")).isNull();
            }
        }
    }

    private static void insert(UUID entryId, UUID eventId, UUID tenantId, String eventType,
                               String payload, Instant createdAt) {
        pool.preparedQuery("""
                INSERT INTO platform_outbox
                    (entry_id, event_id, tenant_id, event_type, payload, created_at, available_at)
                VALUES ($1, $2, $3, $4, CAST($5 AS jsonb), $6, $6)
                """).execute(Tuple.of(entryId, eventId, tenantId, eventType, payload,
                createdAt.atOffset(java.time.ZoneOffset.UTC))).await().indefinitely();
    }

    private static DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private static void executeMigration(Statement statement, String resource) throws Exception {
        try (InputStream input = PgOutboxStorePoisonRowPostgresTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource " + resource);
            statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
