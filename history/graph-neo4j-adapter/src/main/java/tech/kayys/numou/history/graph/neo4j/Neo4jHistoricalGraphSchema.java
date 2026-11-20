package tech.kayys.numou.history.graph.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import java.util.Objects;

/** Explicit, idempotent schema bootstrap. Run with a Neo4j principal allowed to create constraints. */
public final class Neo4jHistoricalGraphSchema {
    private static final String RELATIONSHIP_CONSTRAINT = """
        CREATE CONSTRAINT history_relationship_identity IF NOT EXISTS
        FOR (r:HistoricalRelationship)
        REQUIRE (r.scopeKey, r.relationshipId) IS UNIQUE
        """;
    private static final String ENTITY_CONSTRAINT = """
        CREATE CONSTRAINT history_entity_identity IF NOT EXISTS
        FOR (e:HistoricalEntity)
        REQUIRE (e.scopeKey, e.kind, e.entityId) IS UNIQUE
        """;
    private static final String GENERATION_CONSTRAINT = """
        CREATE CONSTRAINT history_projection_generation_identity IF NOT EXISTS
        FOR (g:HistoricalProjectionGeneration)
        REQUIRE (g.scopeKey, g.generationId) IS UNIQUE
        """;
    private static final String ACTIVE_GENERATION_CONSTRAINT = """
        CREATE CONSTRAINT history_projection_active_scope IF NOT EXISTS
        FOR (a:HistoricalProjectionActiveGeneration)
        REQUIRE a.scopeKey IS UNIQUE
        """;
    private Neo4jHistoricalGraphSchema() {}
    public static void ensure(Driver driver, String database) {
        Objects.requireNonNull(driver, "driver");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database is required");
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run(RELATIONSHIP_CONSTRAINT).consume();
                tx.run(ENTITY_CONSTRAINT).consume();
                tx.run(GENERATION_CONSTRAINT).consume();
                tx.run(ACTIVE_GENERATION_CONSTRAINT).consume();
                return null;
            });
        }
    }
}