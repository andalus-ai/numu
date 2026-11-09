package tech.kayys.numou.history.graph.neo4j;

import org.neo4j.driver.*;
import tech.kayys.numou.history.graph.application.HistoricalProjectionInspectionPort;
import tech.kayys.numou.history.graph.application.GenerationTargetedHistoricalProjectionInspectionPort;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only, keyset-paginated inspection of one public or tenant graph scope. */
public final class Neo4jHistoricalProjectionInspectionAdapter implements GenerationTargetedHistoricalProjectionInspectionPort {
    private final Driver driver;
    private final String database;
    private final HistoricalProjectionGenerationStore generations;

    public Neo4jHistoricalProjectionInspectionAdapter(Driver driver, String database) {
        this(driver, database, null);
    }

    public Neo4jHistoricalProjectionInspectionAdapter(Driver driver, String database,
            HistoricalProjectionGenerationStore generations) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database is required");
        this.database = database;
        this.generations = generations;
    }

    @Override
    public Optional<ProjectedRelationship> find(UUID tenantId, UUID relationshipId) {
        return findAtScope(activeScope(tenantId), relationshipId);
    }

    @Override
    public Optional<ProjectedRelationship> findInGeneration(UUID tenantId, UUID generationId, UUID relationshipId) {
        Objects.requireNonNull(generationId, "generationId");
        return findAtScope(baseScope(tenantId) + "::" + generationId, relationshipId);
    }

    private Optional<ProjectedRelationship> findAtScope(String scope, UUID relationshipId) {
        String query = """
            MATCH (r:HistoricalRelationship {scopeKey:$scope, relationshipId:$id})
            RETURN r.relationshipId AS id, r.revision AS revision, r.eventKey AS eventKey,
                   r.relationshipKind AS relationshipKind, r.subjectKind AS subjectKind,
                   r.subjectId AS subjectId, r.objectKind AS objectKind, r.objectId AS objectId,
                   r.payloadHash AS payloadHash
            """;
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            var cursor = s.run(query, Values.parameters("scope", scope, "id", relationshipId.toString()));
            if (!cursor.hasNext()) return Optional.empty();
            return Optional.of(map(cursor.single()));
        }
    }

    @Override
    public List<ProjectedRelationship> nextPage(UUID tenantId, String afterRelationshipId, int limit) {
        return nextPageAtScope(activeScope(tenantId), afterRelationshipId, limit);
    }

    @Override
    public List<ProjectedRelationship> nextPageInGeneration(UUID tenantId, UUID generationId,
            String afterRelationshipId, int limit) {
        Objects.requireNonNull(generationId, "generationId");
        return nextPageAtScope(baseScope(tenantId) + "::" + generationId, afterRelationshipId, limit);
    }

    private List<ProjectedRelationship> nextPageAtScope(String scope, String afterRelationshipId, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        String query = """
            MATCH (r:HistoricalRelationship {scopeKey:$scope})
            WHERE $after IS NULL OR r.relationshipId > $after
            RETURN r.relationshipId AS id, r.revision AS revision, r.eventKey AS eventKey,
                   r.relationshipKind AS relationshipKind, r.subjectKind AS subjectKind,
                   r.subjectId AS subjectId, r.objectKind AS objectKind, r.objectId AS objectId,
                   r.payloadHash AS payloadHash
            ORDER BY r.relationshipId
            LIMIT $limit
            """;
        List<ProjectedRelationship> result = new ArrayList<>();
        try (Session s = driver.session(SessionConfig.forDatabase(database))) {
            var cursor = s.run(query, Values.parameters("scope", activeScope(tenantId),
                    "after", afterRelationshipId, "limit", limit));
            while (cursor.hasNext()) result.add(map(cursor.next()));
        }
        return List.copyOf(result);
    }

    private static ProjectedRelationship map(org.neo4j.driver.Record r) {
        Value hash = r.get("payloadHash");
        return new ProjectedRelationship(UUID.fromString(r.get("id").asString()),
                r.get("revision").asLong(), r.get("eventKey").asString(),
                r.get("relationshipKind").asString(), r.get("subjectKind").asString(),
                r.get("subjectId").asString(), r.get("objectKind").asString(),
                r.get("objectId").asString(), hash.isNull() ? null : hash.asString());
    }

    private String activeScope(UUID tenantId) {
        String scope = baseScope(tenantId);
        if (generations == null) return scope;
        return generations.active(tenantId)
                .map(generation -> scope + "::" + generation.generationId())
                .orElse(scope);
    }

    private static String baseScope(UUID tenantId) {
        return tenantId == null ? "public" : tenantId.toString();
    }
}
