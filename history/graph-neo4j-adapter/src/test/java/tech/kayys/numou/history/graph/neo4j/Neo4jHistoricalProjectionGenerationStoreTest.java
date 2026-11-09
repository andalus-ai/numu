package tech.kayys.numou.history.graph.neo4j;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Status;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class Neo4jHistoricalProjectionGenerationStoreTest {
    @Container static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("test-password");
    static Driver driver;
    Neo4jHistoricalProjectionGenerationStore store;

    @BeforeAll static void start() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"));
        Neo4jHistoricalGraphSchema.ensure(driver, "neo4j");
    }
    @AfterAll static void stop() { if (driver != null) driver.close(); }
    @BeforeEach void setUp() { store = new Neo4jHistoricalProjectionGenerationStore(driver, "neo4j"); }

    @Test void activatesAtomicallyRetainsPreviousAndSupportsRollback() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-09T07:00:00Z");
        Generation first = store.create(tenant, UUID.randomUUID(), now);
        assertTrue(store.markReady(tenant, first.generationId(), now.plusSeconds(1)));
        assertTrue(store.activate(tenant, first.generationId(), null, now.plusSeconds(2)));

        Generation second = store.create(tenant, UUID.randomUUID(), now.plusSeconds(3));
        assertTrue(store.markReady(tenant, second.generationId(), now.plusSeconds(4)));
        assertTrue(store.activate(tenant, second.generationId(), first.generationId(), now.plusSeconds(5)));

        assertEquals(second.generationId(), store.active(tenant).orElseThrow().generationId());
        assertEquals(Status.RETAINED, store.find(tenant, first.generationId()).orElseThrow().status());
        assertFalse(store.activate(tenant, first.generationId(), first.generationId(), now.plusSeconds(6)));

        assertTrue(store.activate(tenant, first.generationId(), second.generationId(), now.plusSeconds(7)));
        assertEquals(first.generationId(), store.active(tenant).orElseThrow().generationId());
        assertEquals(Status.RETAINED, store.find(tenant, second.generationId()).orElseThrow().status());
    }

    @Test void publicAndTenantGenerationPointersAreIsolated() {
        Instant now = Instant.parse("2026-11-09T07:00:00Z");
        UUID tenant = UUID.randomUUID();
        Generation publicGeneration = store.create(null, UUID.randomUUID(), now);
        Generation tenantGeneration = store.create(tenant, UUID.randomUUID(), now);
        assertTrue(store.markReady(null, publicGeneration.generationId(), now));
        assertTrue(store.markReady(tenant, tenantGeneration.generationId(), now));
        assertTrue(store.activate(null, publicGeneration.generationId(), null, now));
        assertTrue(store.activate(tenant, tenantGeneration.generationId(), null, now));
        assertEquals(publicGeneration.generationId(), store.active(null).orElseThrow().generationId());
        assertEquals(tenantGeneration.generationId(), store.active(tenant).orElseThrow().generationId());
    }
}
