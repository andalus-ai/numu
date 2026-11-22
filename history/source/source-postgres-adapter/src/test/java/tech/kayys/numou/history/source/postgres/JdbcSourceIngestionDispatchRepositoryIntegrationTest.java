package tech.kayys.numou.history.source.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.source.domain.*;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSourceIngestionDispatchRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("history_dispatch").withUsername("admin").withPassword("admin-password");

    static JdbcHistoricalSourceRepository sources;
    static JdbcSourceIngestionRepository ingestions;
    static JdbcSourceIngestionDispatchRepository dispatch;

    @BeforeAll
    static void start() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE history_dispatch_app LOGIN PASSWORD 'dispatch-password' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO history_dispatch_app");
            statement.execute("GRANT SELECT, INSERT, UPDATE ON historical_source TO history_dispatch_app");
            statement.execute("GRANT SELECT, INSERT ON historical_source_revision, historical_source_ingestion TO history_dispatch_app");
            statement.execute("GRANT SELECT, INSERT, UPDATE ON historical_source_ingestion_dispatch TO history_dispatch_app");
            statement.execute("GRANT SELECT, INSERT ON historical_source_ingestion_dispatch_event TO history_dispatch_app");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser("history_dispatch_app");
        dataSource.setPassword("dispatch-password");
        sources = new JdbcHistoricalSourceRepository(dataSource);
        ingestions = new JdbcSourceIngestionRepository(dataSource);
        dispatch = new JdbcSourceIngestionDispatchRepository(dataSource);
    }

    @Test
    void claimsAreExclusiveAndExpiredLeaseFencesThePreviousWorker() {
        UUID tenant = UUID.randomUUID();
        var request = enqueue(tenant);
        var first = dispatch.claimNext(tenant, "worker:one", NOW, NOW.plusSeconds(30)).orElseThrow();
        assertTrue(dispatch.claimNext(tenant, "worker:two", NOW, NOW.plusSeconds(30)).isEmpty());

        Instant expiredAt = NOW.plusSeconds(31);
        var second = dispatch.claimNext(tenant, "worker:two", expiredAt, expiredAt.plusSeconds(30)).orElseThrow();
        assertEquals(2, second.attempt());
        assertNotEquals(first.leaseToken(), second.leaseToken());
        assertFalse(dispatch.complete(tenant, request.id(), "worker:one", first.leaseToken(), expiredAt));
        assertFalse(dispatch.complete(tenant, request.id(), "worker:intruder", second.leaseToken(), expiredAt));
        assertTrue(dispatch.complete(tenant, request.id(), "worker:two", second.leaseToken(), expiredAt));
        assertFalse(dispatch.complete(tenant, request.id(), "worker:two", second.leaseToken(), expiredAt.plusSeconds(1)));

        assertEquals(4, eventCount(tenant, request.id()));
        assertThrows(java.sql.SQLException.class, () -> {
            try (var connection = java.sql.DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.prepareStatement(
                         "update historical_source_ingestion_dispatch_event set error=? where tenant_id=? and ingestion_id=?")) {
                statement.setString(1, "rewritten");
                statement.setObject(2, tenant);
                statement.setObject(3, request.id());
                statement.executeUpdate();
            }
        });
    }

    @Test
    void retryAndDeadLetterTransitionsAreTenantScopedAndAudited() {
        UUID tenant = UUID.randomUUID();
        var request = enqueue(tenant);
        var lease = dispatch.claimNext(tenant, "worker:ocr", NOW, NOW.plusSeconds(30)).orElseThrow();
        assertFalse(dispatch.scheduleRetry(UUID.randomUUID(), request.id(), "worker:ocr", lease.leaseToken(),
                NOW.plusSeconds(60), NOW.plusSeconds(1), "not this tenant"));
        assertTrue(dispatch.scheduleRetry(tenant, request.id(), "worker:ocr", lease.leaseToken(),
                NOW.plusSeconds(60), NOW.plusSeconds(1), "temporary extraction failure"));
        var retry = dispatch.claimNext(tenant, "worker:ocr", NOW.plusSeconds(60), NOW.plusSeconds(90)).orElseThrow();
        assertEquals(2, retry.attempt());
        assertTrue(dispatch.deadLetter(tenant, request.id(), "worker:ocr", retry.leaseToken(),
                NOW.plusSeconds(61), "unsupported encrypted archive"));
        assertTrue(dispatch.claimNext(tenant, "worker:another", NOW.plusSeconds(120), NOW.plusSeconds(150)).isEmpty());
        assertEquals(5, eventCount(tenant, request.id()));
    }

    private static SourceIngestionRequest enqueue(UUID tenant) {
        var source = HistoricalSource.register(HistoricalSourceId.generate(), HistoricalSourceKind.WEBSITE,
                "Archive portal", null, null, "https://archive.example/catalogue/1",
                "Archive", null, "en", NOW);
        assertTrue(sources.register(tenant, source));
        var request = SourceIngestionRequest.fromCanonicalUri(source.id(), source.canonicalUri(),
                "actor:historian", NOW);
        assertTrue(ingestions.appendIngestion(tenant, request));
        return request;
    }

    private static int eventCount(UUID tenant, UUID ingestionId) {
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement(
                     "select count(*) from historical_source_ingestion_dispatch_event where tenant_id=? and ingestion_id=?")) {
            statement.setObject(1, tenant);
            statement.setObject(2, ingestionId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
