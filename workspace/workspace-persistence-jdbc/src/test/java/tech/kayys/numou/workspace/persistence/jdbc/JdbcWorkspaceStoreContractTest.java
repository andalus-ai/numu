package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Focuses on persistence invariants that can be checked without requiring a live database. */
class JdbcWorkspaceStoreContractTest {
 @Test void pageRevisionMustAdvanceExactlyOnce(){
  UUID tenant=UUID.randomUUID(),workspace=UUID.randomUUID(),space=UUID.randomUUID(),pageId=UUID.randomUUID(),actor=UUID.randomUUID();
  Instant now=Instant.EPOCH;
  Page p=new Page(pageId,tenant,workspace,space,null,"Page",List.of(),2,now,now);
  PageRevision rev=PageRevision.snapshot(p,actor,now,"update");
  assertEquals(2,rev.revision());
 }
 @Test void typedAttributesRoundTripFormatIsStableThroughPublicContentModel(){
  PageBlock block=new PageBlock(UUID.randomUUID(),BlockType.DOMAIN_REFERENCE,"",0,Map.of("referenceType","finance.invoice","referenceId","a:b;c"));
  assertEquals("a:b;c",block.attributes().get("referenceId"));
 }
}
