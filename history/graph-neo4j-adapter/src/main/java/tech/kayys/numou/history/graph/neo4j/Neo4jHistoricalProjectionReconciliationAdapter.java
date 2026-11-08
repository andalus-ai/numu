package tech.kayys.numou.history.graph.neo4j;

import org.neo4j.driver.*;
import tech.kayys.numou.history.graph.application.HistoricalProjectionReconciliationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only, bounded inspection of the optional Neo4j graph projection. */
public final class Neo4jHistoricalProjectionReconciliationAdapter implements HistoricalProjectionReconciliationPort {
    private final Driver driver;
    private final String database;

    public Neo4jHistoricalProjectionReconciliationAdapter(Driver driver, String database) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database is required");
        this.database = database;
    }

    @Override
    public Optional<ProjectionState> find(UUID tenantId, UUID relationshipId) {
        String scope = tenantId == null ? "public" : tenantId.toString();
        String query = """
                MATCH (r:HistoricalRelationship {scopeKey:$scope, relationshipId:$id})
                RETURN r.revision AS revision, r.eventKey AS eventKey, r.payloadHash AS payloadHash
                """;
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            var result = session.run(query, Values.parameters("scope", scope, "id", relationshipId.toString()));
            if (!result.hasNext()) return Optional.empty();
            var row = result.single();
            Value hash = row.get("payloadHash");
            return Optional.of(new ProjectionState(row.get("revision").asLong(),
                    row.get("eventKey").asString(), hash.isNull() ? null : hash.asString()));
        }
    }

    @Override
    public List<UUID> listRelationshipIds(UUID tenantId, UUID afterExclusive, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        String scope = tenantId == null ? "public" : tenantId.toString();
        String query = """
                MATCH (r:HistoricalRelationship {scopeKey:$scope})
                WHERE $after IS NULL OR r.relationshipId > $after
                RETURN r.relationshipId AS id
                ORDER BY id
                LIMIT $limit
                """;
        List<UUID> ids = new ArrayList<>();
        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            var result = session.run(query, Values.parameters("scope", scope,
                    "after", afterExclusive == null ? null : afterExclusive.toString(), "limit", limit));
            while (result.hasNext()) ids.add(UUID.fromString(result.next().get("id").asString()));
        }
        return List.copyOf(ids);
    }
}
