package tech.kayys.numou.history.graph.neo4j;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class Neo4jHistoricalRelationshipProjectionAdapterTest {
    @Container static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("test-password");
    static Driver driver;
    static Neo4jHistoricalRelationshipProjectionAdapter adapter;
    @BeforeAll static void start() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.basic("neo4j", "test-password"));
        Neo4jHistoricalGraphSchema.ensure(driver, "neo4j");
        adapter = new Neo4jHistoricalRelationshipProjectionAdapter(driver, "neo4j");
    }
    @AfterAll static void stop() { if (driver != null) driver.close(); }

    @Test void duplicateAndStaleEventsDoNotRegressProjection() {
        UUID relationship = UUID.randomUUID();
        var first = event(relationship, 1, null, "PERSON", "person-1", "COLLECTIVE", "group-1");
        adapter.project(first); adapter.project(first);
        adapter.project(event(relationship, 3, null, "PERSON", "person-1", "COLLECTIVE", "group-2"));
        adapter.project(event(relationship, 2, null, "PERSON", "person-old", "COLLECTIVE", "group-old"));
        try (Session s = driver.session()) {
            var result = s.run("MATCH (r:HistoricalRelationship {relationshipId:$id,scopeKey:'public'}) RETURN r.revision AS revision", Values.parameters("id", relationship.toString())).single();
            assertEquals(3, result.get("revision").asLong());
            assertEquals(1, s.run("MATCH (r:HistoricalRelationship {relationshipId:$id,scopeKey:'public'})-[:SUBJECT]->(:HistoricalEntity {entityId:'person-1'}) RETURN count(*) AS n", Values.parameters("id", relationship.toString())).single().get("n").asLong());
            assertEquals(1, s.run("MATCH (r:HistoricalRelationship {relationshipId:$id,scopeKey:'public'})-[:OBJECT]->(:HistoricalEntity {entityId:'group-2'}) RETURN count(*) AS n", Values.parameters("id", relationship.toString())).single().get("n").asLong());
        }
    }

    @Test void tenantProjectionIsScopedAndNotVisibleAsPublic() {
        UUID relationship = UUID.randomUUID(), tenant = UUID.randomUUID();
        adapter.project(event(relationship, 1, tenant, "PERSON", "same-person", "COLLECTIVE", "same-group"));
        try (Session s = driver.session()) {
            assertEquals(1, s.run("MATCH (:HistoricalRelationship {relationshipId:$id,scopeKey:$scope}) RETURN count(*) AS n", Values.parameters("id", relationship.toString(), "scope", tenant.toString())).single().get("n").asLong());
            assertEquals(0, s.run("MATCH (:HistoricalRelationship {relationshipId:$id,scopeKey:'public'}) RETURN count(*) AS n", Values.parameters("id", relationship.toString())).single().get("n").asLong());
        }
    }

    private static ClaimedProjectionEvent event(UUID id, long revision, UUID tenant, String sk, String sid, String ok, String oid) {
        String payload = """
          {"relationshipId":"%s","revision":%d,"subject":{"kind":"%s","id":"%s"},"object":{"kind":"%s","id":"%s"},"relationshipKind":"founded","description":"test","validity":null}
          """.formatted(id, revision, sk, sid, ok, oid);
        return new ClaimedProjectionEvent(UUID.randomUUID(), "history-rel:"+id+":"+revision, Neo4jHistoricalRelationshipProjectionAdapter.EVENT_TYPE, 1, tenant, id, revision, payload, Instant.parse("2026-01-01T00:00:00Z"), 1, 1, UUID.randomUUID(), Instant.parse("2026-01-01T00:01:00Z"));
    }
}