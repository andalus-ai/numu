package tech.kayys.numou.history.graph.neo4j;

import org.neo4j.driver.*;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Generation;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore.Status;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Neo4j implementation of the generation registry. Generation lifecycle and active
 * pointer changes are transactional; old generations are retained for rollback.
 */
public final class Neo4jHistoricalProjectionGenerationStore implements HistoricalProjectionGenerationStore {
    private final Driver driver;
    private final String database;

    public Neo4jHistoricalProjectionGenerationStore(Driver driver, String database) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database is required");
        this.database = database;
    }

    @Override
    public Generation create(UUID tenantId, UUID generationId, Instant now) {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(now, "now");
        String query = """
            CREATE (g:HistoricalProjectionGeneration {scopeKey:$scope, generationId:$id,
                status:'BUILDING', createdAt:$now, updatedAt:$now})
            RETURN g.generationId AS id, g.status AS status, g.createdAt AS createdAt, g.updatedAt AS updatedAt
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            return s.executeWrite(tx -> {
                var row = tx.run(query, Values.parameters("scope", scope(tenantId),
                        "id", generationId.toString(), "now", now.toString())).single();
                Generation result = map(row, tenantId);
                if (result.status() != Status.BUILDING) {
                    throw new IllegalStateException("generation identifier already exists");
                }
                return result;
            });
        }
    }

    @Override
    public Optional<Generation> find(UUID tenantId, UUID generationId) {
        String query = """
            MATCH (g:HistoricalProjectionGeneration {scopeKey:$scope, generationId:$id})
            RETURN g.generationId AS id, g.status AS status, g.createdAt AS createdAt, g.updatedAt AS updatedAt
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            var cursor = s.run(query, Values.parameters("scope", scope(tenantId), "id", generationId.toString()));
            return cursor.hasNext() ? Optional.of(map(cursor.single(), tenantId)) : Optional.empty();
        }
    }

    @Override
    public Optional<Generation> active(UUID tenantId) {
        String query = """
            MATCH (a:HistoricalProjectionActiveGeneration {scopeKey:$scope})
            MATCH (g:HistoricalProjectionGeneration {scopeKey:$scope, generationId:a.generationId, status:'ACTIVE'})
            RETURN g.generationId AS id, g.status AS status, g.createdAt AS createdAt, g.updatedAt AS updatedAt
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            var cursor = s.run(query, Values.parameters("scope", scope(tenantId)));
            return cursor.hasNext() ? Optional.of(map(cursor.single(), tenantId)) : Optional.empty();
        }
    }

    @Override
    public boolean markReady(UUID tenantId, UUID generationId, Instant now) {
        String query = """
            MATCH (g:HistoricalProjectionGeneration {scopeKey:$scope, generationId:$id, status:'BUILDING'})
            SET g.status='READY', g.updatedAt=$now
            RETURN count(g) AS changed
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            return s.executeWrite(tx -> tx.run(query, Values.parameters("scope", scope(tenantId),
                    "id", generationId.toString(), "now", now.toString())).single().get("changed").asLong() == 1);
        }
    }

    @Override
    public boolean activate(UUID tenantId, UUID generationId, UUID expectedActiveGenerationId, Instant now) {
        String query = """
            MATCH (next:HistoricalProjectionGeneration {scopeKey:$scope, generationId:$nextId})
            WHERE next.status IN ['READY','RETAINED']
            MERGE (active:HistoricalProjectionActiveGeneration {scopeKey:$scope})
            WITH next, active
            WHERE ($expectedId IS NULL AND active.generationId IS NULL)
               OR active.generationId = $expectedId
            OPTIONAL MATCH (old:HistoricalProjectionGeneration {
                scopeKey:$scope, generationId:active.generationId, status:'ACTIVE'
            })
            SET old.status='RETAINED', old.updatedAt=$now
            SET next.status='ACTIVE', next.updatedAt=$now,
                active.generationId=next.generationId, active.updatedAt=$now
            RETURN count(next) AS changed
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            return s.executeWrite(tx -> tx.run(query, Values.parameters("scope", scope(tenantId),
                    "nextId", generationId.toString(),
                    "expectedId", expectedActiveGenerationId == null ? null : expectedActiveGenerationId.toString(),
                    "now", now.toString())).single().get("changed").asLong() == 1);
        }
    }

    private static Generation map(org.neo4j.driver.Record row, UUID tenantId) {
        return new Generation(UUID.fromString(row.get("id").asString()), tenantId,
                Status.valueOf(row.get("status").asString()),
                Instant.parse(row.get("createdAt").asString()),
                Instant.parse(row.get("updatedAt").asString()));
    }

    private static String scope(UUID tenantId) {
        return tenantId == null ? "public" : tenantId.toString();
    }
}
