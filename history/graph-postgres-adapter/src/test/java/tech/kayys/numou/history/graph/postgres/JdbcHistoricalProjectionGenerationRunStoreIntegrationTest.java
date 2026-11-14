package tech.kayys.numou.history.graph.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHistoricalProjectionGenerationRunStoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource source;

    @BeforeAll static void migrate() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(PG.getJdbcUrl());
        ds.setUser(PG.getUsername());
        ds.setPassword(PG.getPassword());
        source = ds;
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    }

    @Test void operationIdIsIdempotentAndBoundToTenantScope() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        UUID tenant = UUID.randomUUID(), operation = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-14T00:00:00Z");
        var first = store.create(tenant, operation, UUID.randomUUID(), UUID.randomUUID(), null, now);
        var retry = store.create(tenant, operation, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(1));

        assertEquals(first.generationId(), retry.generationId());
        assertEquals(first.rebuildRunId(), retry.rebuildRunId());
        assertEquals(first.expectedActiveGenerationId(), retry.expectedActiveGenerationId());
        assertTrue(store.find(UUID.randomUUID(), operation).isEmpty());
        assertEquals(first, store.find(tenant, operation).orElseThrow());
    }

    @Test void lifecycleTransitionsAreCompareAndSetAndFailuresRemainObservable() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        UUID operation = UUID.randomUUID();
        store.create(null, operation, UUID.randomUUID(), UUID.randomUUID(), null, Instant.now());

        assertTrue(store.transition(null, operation,
                HistoricalProjectionGenerationRunStore.Status.PREPARING,
                HistoricalProjectionGenerationRunStore.Status.REPLAYING, Instant.now()));
        assertFalse(store.transition(null, operation,
                HistoricalProjectionGenerationRunStore.Status.PREPARING,
                HistoricalProjectionGenerationRunStore.Status.REPLAYING, Instant.now()));

        store.recordFailure(null, operation, "Neo4j temporarily unavailable", Instant.now());
        var updated = store.find(null, operation).orElseThrow();
        assertEquals(1, updated.failureCount());
        assertEquals("Neo4j temporarily unavailable", updated.lastError());
        assertEquals(HistoricalProjectionGenerationRunStore.Status.REPLAYING, updated.status());
    }

    @Test void rejectsIllegalStateTransitions() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        assertThrows(IllegalArgumentException.class, () -> store.transition(null, UUID.randomUUID(),
                HistoricalProjectionGenerationRunStore.Status.PREPARING,
                HistoricalProjectionGenerationRunStore.Status.ACTIVE, Instant.now()));
    }
}
