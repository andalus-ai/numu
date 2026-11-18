package tech.kayys.numou.history.graph.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Run;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Status;

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

    @Test void operationIdIsIdempotentAndTenantScoped() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        UUID tenant = UUID.randomUUID(), operation = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        Run first = store.create(tenant, operation, UUID.randomUUID(), UUID.randomUUID(), null, now);
        Run retry = store.create(tenant, operation, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(1));

        assertEquals(first.generationId(), retry.generationId());
        assertEquals(first.rebuildRunId(), retry.rebuildRunId());
        assertEquals(first.expectedActiveGenerationId(), retry.expectedActiveGenerationId());
        assertTrue(store.find(UUID.randomUUID(), operation).isEmpty());
        assertEquals(first, store.find(tenant, operation).orElseThrow());
    }

    @Test void transitionsAreCompareAndSetAndFailuresAreDurable() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        UUID operation = UUID.randomUUID();
        store.create(null, operation, UUID.randomUUID(), UUID.randomUUID(), null, Instant.now());

        assertTrue(store.transition(null, operation, Status.PREPARING, Status.REPLAYING, Instant.now()));
        assertFalse(store.transition(null, operation, Status.PREPARING, Status.REPLAYING, Instant.now()));
        store.recordFailure(null, operation, "temporary provider outage\nretry", Instant.now());

        Run current = store.find(null, operation).orElseThrow();
        assertEquals(Status.REPLAYING, current.status());
        assertEquals(1, current.failureCount());
        assertEquals("temporary provider outage retry", current.lastError());
    }

    @Test void activatedTimestampIsSetOnlyWithTerminalTransition() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        UUID operation = UUID.randomUUID();
        store.create(null, operation, UUID.randomUUID(), UUID.randomUUID(), null, Instant.now());
        assertTrue(store.transition(null, operation, Status.PREPARING, Status.REPLAYING, Instant.now()));
        assertTrue(store.transition(null, operation, Status.REPLAYING, Status.VALIDATING, Instant.now()));
        assertTrue(store.transition(null, operation, Status.VALIDATING, Status.READY, Instant.now()));
        assertTrue(store.transition(null, operation, Status.READY, Status.ACTIVATING, Instant.now()));
        assertTrue(store.transition(null, operation, Status.ACTIVATING, Status.ACTIVE, Instant.now()));
        assertNotNull(store.find(null, operation).orElseThrow().activatedAt());
    }

    @Test void illegalTransitionsAreRejected() {
        var store = new JdbcHistoricalProjectionGenerationRunStore(source);
        assertThrows(IllegalArgumentException.class, () -> store.transition(null, UUID.randomUUID(),
                Status.PREPARING, Status.ACTIVE, Instant.now()));
    }
}
