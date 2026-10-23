package tech.kayys.numou.workspace.domain;
import org.junit.jupiter.api.Test; import java.time.Instant; import java.util.List; import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PageInvariantTest {
 @Test void rejectsDuplicateBlockIdsAndPositions(){UUID id=UUID.randomUUID();PageBlock a=new PageBlock(id,BlockType.PARAGRAPH,"a",0,null);PageBlock b=new PageBlock(id,BlockType.HEADING,"b",1,null);
  assertThrows(IllegalArgumentException.class,()->Page.create(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"title",List.of(a,b),Instant.EPOCH));
  PageBlock c=PageBlock.text(BlockType.PARAGRAPH,"c",0);assertThrows(IllegalArgumentException.class,()->Page.create(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"title",List.of(a,c),Instant.EPOCH));
 }
 @Test void domainReferenceRequiresOpaqueType(){assertThrows(IllegalArgumentException.class,()->new PageBlock(UUID.randomUUID(),BlockType.DOMAIN_REFERENCE,"",0,java.util.Map.of()));}
 @Test void spaceSlugIsNormalizedAndValidated(){Space s=Space.create(UUID.randomUUID(),UUID.randomUUID(),"Operations","Ops-Team",Instant.EPOCH);assertEquals("ops-team",s.slug());assertThrows(IllegalArgumentException.class,()->Space.create(UUID.randomUUID(),UUID.randomUUID(),"Bad","bad slug",Instant.EPOCH));}
}
