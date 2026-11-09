package tech.kayys.numou.history.graph.neo4j;
import org.junit.jupiter.api.*;import org.neo4j.driver.*;import org.testcontainers.containers.Neo4jContainer;import org.testcontainers.junit.jupiter.Container;import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
@Testcontainers(disabledWithoutDocker = true)
class Neo4jHistoricalProjectionInspectionAdapterTest {
 @Container static final Neo4jContainer<?> NEO4J=new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("test-password");static Driver driver;static Neo4jHistoricalRelationshipProjectionAdapter projector;static Neo4jHistoricalProjectionInspectionAdapter inspector;
 @BeforeAll static void start(){driver=GraphDatabase.driver(NEO4J.getBoltUrl(),AuthTokens.basic("neo4j","test-password"));Neo4jHistoricalGraphSchema.ensure(driver,"neo4j");projector=new Neo4jHistoricalRelationshipProjectionAdapter(driver,"neo4j");inspector=new Neo4jHistoricalProjectionInspectionAdapter(driver,"neo4j");}
 @AfterAll static void stop(){if(driver!=null)driver.close();}
 @Test void inspectionFindAndKeysetPagesRespectScope(){
  UUID tenant=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();projector.project(event(first,tenant));projector.project(event(second,tenant));
  var found=inspector.find(tenant,first).orElseThrow();assertEquals(1,found.revision());assertEquals("founded",found.relationshipKind());
  var pageOne=inspector.nextPage(tenant,null,1);assertEquals(1,pageOne.size());var pageTwo=inspector.nextPage(tenant,pageOne.get(0).relationshipId().toString(),10);
  assertEquals(1,pageTwo.size());assertNotEquals(pageOne.get(0).relationshipId(),pageTwo.get(0).relationshipId());assertTrue(inspector.find(null,first).isEmpty());assertTrue(inspector.nextPage(null,null,10).isEmpty());
 }
 private static tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent event(UUID id,UUID tenant){
  String payload="""
   {"relationshipId":"%s","revision":1,"subject":{"kind":"PERSON","id":"p-%s"},"object":{"kind":"COLLECTIVE","id":"c-%s"},"relationshipKind":"founded","description":"inspection test","validity":null}
   """.formatted(id,id,id);
  return new tech.kayys.numou.history.graph.spi.HistoricalProjectionOutboxStore.ClaimedProjectionEvent(UUID.randomUUID(),"history-rel:"+id+":1",Neo4jHistoricalRelationshipProjectionAdapter.EVENT_TYPE,1,tenant,id,1,payload,Instant.parse("2026-01-01T00:00:00Z"),1,1,UUID.randomUUID(),Instant.parse("2026-01-01T00:01:00Z"));
 }
}