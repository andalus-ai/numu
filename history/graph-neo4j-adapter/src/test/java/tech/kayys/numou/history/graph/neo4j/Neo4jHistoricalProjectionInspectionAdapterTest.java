package tech.kayys.numou.history.graph.neo4j;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class Neo4jHistoricalProjectionInspectionAdapterTest {
    @Container static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("test-password");
    static Driver driver;
    static Neo4jHistoricalRelationshipProjectionAdapter projector;
    static Neo4jHistoricalProjectionInspectionAdapter inspector;

    @BeforeAll static void start() {
        driver=GraphDatabase.driver(NEO4J.getBoltUrl(),AuthTokens.basic("neo4j","test-password"));
        Neo4jHistoricalGraphSchema.ensure(driver,"neo4j");
        projector=new Neo4jHistoricalRelationshipProjectionAdapter(driver,"neo4j");
        inspector=new Neo4jHistoricalProjectionInspectionAdapter(driver,"neo4j");
    }
    @AfterAll static void stop(){if(driver!=null)driver.close();}

    @Test void keysetPagesAreOrderedAndScopedToTenant() {
        UUID tenant=UUID.randomUUID();
        UUID first=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second=UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID other=UUID.fromString("00000000-0000-0000-0000-000000000003");
        projector.project(event(first,tenant)); projector.project(event(second,tenant)); projector.project(event(other,null));
        var page1=inspector.nextPage(tenant,null,1);
        assertEquals(List.of(first),page1.stream().map(r->r.relationshipId()).toList());
        var page2=inspector.nextPage(tenant,first.toString(),10);
        assertEquals(List.of(second),page2.stream().map(r->r.relationshipId()).toList());
        assertTrue(inspector.find(tenant,other).isEmpty());
        assertTrue(inspector.find(null,other).isPresent());
    }

    private static tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent event(UUID id,UUID tenant) {
        String payload="""
          {"relationshipId":"%s","revision":1,"subject":{"kind":"PERSON","id":"p-%s"},"object":{"kind":"COLLECTIVE","id":"c-%s"},"relationshipKind":"founded","description":"integration","validity":null}
          """.formatted(id,id,id);
        return new tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent(
          UUID.randomUUID(),"event-"+id,Neo4jHistoricalRelationshipProjectionAdapter.EVENT_TYPE,1,tenant,id,1,
          payload,java.time.Instant.parse("2026-01-01T00:00:00Z"),1,1,UUID.randomUUID(),java.time.Instant.parse("2026-01-01T00:01:00Z"));
    }
}