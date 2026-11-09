package tech.kayys.numou.history.graph.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.*;
import tech.kayys.numou.history.graph.application.GenerationTargetedHistoricalRelationshipProjectionPort;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Optional Neo4j projection. PostgreSQL outbox events are the only input;
 * Neo4j records are disposable and can be rebuilt from the authoritative store.
 */
public final class Neo4jHistoricalRelationshipProjectionAdapter implements GenerationTargetedHistoricalRelationshipProjectionPort {
    public static final String EVENT_TYPE = "history.relationship.revision-appended";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final String TARGET_SCOPES = """
        MERGE (a:HistoricalProjectionActiveGeneration {scopeKey:$baseScope})
        ON CREATE SET a.lockVersion=0
        SET a.lockVersion=coalesce(a.lockVersion,0)+1
        WITH a
        OPTIONAL MATCH (active:HistoricalProjectionGeneration {
            scopeKey:$baseScope, generationId:a.generationId, status:'ACTIVE'
        })
        WITH a, collect(active.generationId) AS activeIds
        OPTIONAL MATCH (building:HistoricalProjectionGeneration {
            scopeKey:$baseScope, status:'BUILDING'
        })
        RETURN activeIds[0] AS activeGenerationId,
               collect(building.generationId) AS buildingGenerationIds
        """;
    private static final String ADVANCE = """
        MERGE (r:HistoricalRelationship {scopeKey: $scopeKey, relationshipId: $relationshipId})
        ON CREATE SET r.revision = 0, r.lockVersion = 0
        SET r.lockVersion = r.lockVersion + 1
        WITH r, (r.revision < $revision OR (r.revision = $revision AND (coalesce(r.payloadHash, '') <> $payloadHash OR coalesce(r.eventKey, '') <> $eventKey OR
             coalesce(r.relationshipKind, '') <> $relationshipKind OR coalesce(r.subjectKind, '') <> $subjectKind OR
             coalesce(r.subjectId, '') <> $subjectId OR coalesce(r.objectKind, '') <> $objectKind OR
             coalesce(r.objectId, '') <> $objectId))) AS apply
        FOREACH (_ IN CASE WHEN apply THEN [1] ELSE [] END |
          SET r.revision = $revision,
              r.eventKey = $eventKey,
              r.payloadHash = $payloadHash,
              r.relationshipKind = $relationshipKind,
              r.description = $description,
              r.validityJson = $validityJson,
              r.recordedAt = $occurredAt,
              r.subjectKind = $subjectKind,
              r.subjectId = $subjectId,
              r.objectKind = $objectKind,
              r.objectId = $objectId
        )
        RETURN apply
        """;
    private static final String REMOVE_ENDPOINTS = """
        MATCH (r:HistoricalRelationship {scopeKey: $scopeKey, relationshipId: $relationshipId})
        OPTIONAL MATCH (r)-[old:SUBJECT|OBJECT]-()
        DELETE old
        """;
    private static final String LINK_ENDPOINTS = """
        MATCH (r:HistoricalRelationship {scopeKey: $scopeKey, relationshipId: $relationshipId})
        MERGE (s:HistoricalEntity {scopeKey: $scopeKey, kind: $subjectKind, entityId: $subjectId})
        MERGE (o:HistoricalEntity {scopeKey: $scopeKey, kind: $objectKind, entityId: $objectId})
        CREATE (r)-[:SUBJECT]->(s)
        CREATE (r)-[:OBJECT]->(o)
        """;

    private final Driver driver;
    private final String database;
    private final ObjectMapper mapper;

    public Neo4jHistoricalRelationshipProjectionAdapter(Driver driver, String database) {
        this(driver, database, new ObjectMapper());
    }

    Neo4jHistoricalRelationshipProjectionAdapter(Driver driver, String database, ObjectMapper mapper) {
        this.driver = Objects.requireNonNull(driver, "driver");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database is required");
        this.database = database;
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void project(ClaimedProjectionEvent event) {
        projectInternal(event, null);
    }

    @Override
    public void projectIntoGeneration(ClaimedProjectionEvent event, UUID generationId) {
        Objects.requireNonNull(generationId, "generationId");
        projectInternal(event, generationId);
    }

    private void projectInternal(ClaimedProjectionEvent event, UUID generationId) {
        Objects.requireNonNull(event, "event");
        if (!EVENT_TYPE.equals(event.eventType())) throw new IllegalArgumentException("Unsupported projection event type: " + event.eventType());
        if (event.schemaVersion() != SUPPORTED_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported projection schema version: " + event.schemaVersion());
        if (event.aggregateRevision() < 1) throw new IllegalArgumentException("aggregate revision must be positive");
        final JsonNode payload;
        try { payload = mapper.readTree(event.payload()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid historical relationship projection payload", e); }

        String relationshipId = requiredText(payload, "relationshipId");
        long payloadRevision = requiredLong(payload, "revision");
        if (!relationshipId.equals(event.aggregateId().toString()) || payloadRevision != event.aggregateRevision()) {
            throw new IllegalArgumentException("Projection envelope and payload identity/revision do not match");
        }
        JsonNode subject = requiredObject(payload, "subject");
        JsonNode object = requiredObject(payload, "object");
        String subjectKind = requiredText(subject, "kind");
        String subjectId = requiredText(subject, "id");
        String objectKind = requiredText(object, "kind");
        String objectId = requiredText(object, "id");
        String relationshipKind = requiredText(payload, "relationshipKind");
        String scopeKey = event.tenantId() == null ? "public" : event.tenantId().toString();
        if (generationId != null) scopeKey = scopeKey + "::" + generationId;
        String validityJson = payload.hasNonNull("validity") ? payload.get("validity").toString() : null;
        Map<String,Object> params = new HashMap<>();
        params.put("scopeKey", scopeKey);
        params.put("relationshipId", relationshipId);
        params.put("revision", event.aggregateRevision());
        params.put("eventKey", event.eventKey());
        params.put("payloadHash", sha256(event.payload()));
        params.put("relationshipKind", relationshipKind);
        params.put("description", payload.path("description").isTextual() ? payload.path("description").asText() : null);
        params.put("validityJson", validityJson);
        params.put("occurredAt", event.occurredAt().toString());
        params.put("subjectKind", subjectKind); params.put("subjectId", subjectId);
        params.put("objectKind", objectKind); params.put("objectId", objectId);

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                List<String> targetScopes = new ArrayList<>();
                if (generationId != null) {
                    targetScopes.add(scopeKey);
                } else {
                    var targets = tx.run(TARGET_SCOPES, Map.of("baseScope", scopeKey)).single();
                    Value activeGeneration = targets.get("activeGenerationId");
                    if (activeGeneration.isNull()) {
                        targetScopes.add(scopeKey);
                    } else {
                        targetScopes.add(scopeKey + "::" + activeGeneration.asString());
                    }
                    for (String building : targets.get("buildingGenerationIds").asList(Value::asString)) {
                        targetScopes.add(scopeKey + "::" + building);
                    }
                }
                for (String targetScope : new LinkedHashSet<>(targetScopes)) {
                    Map<String,Object> targetParams = new HashMap<>(params);
                    targetParams.put("scopeKey", targetScope);
                    boolean advanced = tx.run(ADVANCE, targetParams).single().get("apply").asBoolean();
                    if (advanced) {
                        tx.run(REMOVE_ENDPOINTS, targetParams).consume();
                        tx.run(LINK_ENDPOINTS, targetParams).consume();
                    }
                }
                return null;
            });
        }
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) throw new IllegalArgumentException("Missing object payload field: " + field);
        return value;
    }
    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Missing text payload field: " + field);
        return value.asText();
    }
    private static long requiredLong(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.canConvertToLong()) throw new IllegalArgumentException("Missing numeric payload field: " + field);
        return value.asLong();
    }
}