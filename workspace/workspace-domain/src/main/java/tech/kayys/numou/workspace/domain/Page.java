package tech.kayys.numou.workspace.domain;
import java.time.Instant; import java.util.*;
/** Current page content. Each accepted write advances revision monotonically. */
public record Page(UUID id,UUID tenantId,UUID workspaceId,UUID spaceId,UUID parentPageId,String title,List<PageBlock> blocks,long revision,Instant createdAt,Instant updatedAt) {
 public Page { Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(workspaceId);Objects.requireNonNull(spaceId);if(id.equals(parentPageId))throw new IllegalArgumentException("page cannot be its own parent");title=req(title,"title");blocks=blocks==null?List.of():List.copyOf(blocks);if(revision<1)throw new IllegalArgumentException("revision must be positive");Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt);Set<UUID> ids=new HashSet<>();Set<Integer> positions=new HashSet<>();for(PageBlock b:blocks){if(!ids.add(b.id()))throw new IllegalArgumentException("duplicate block id");if(!positions.add(b.position()))throw new IllegalArgumentException("duplicate block position");} }
 public static Page create(UUID tenant,UUID workspace,UUID space,UUID parent,String title,List<PageBlock> blocks,Instant now){return new Page(UUID.randomUUID(),tenant,workspace,space,parent,title,blocks,1,now,now);}
 public Page revise(String newTitle,List<PageBlock> newBlocks,Instant now){return new Page(id,tenantId,workspaceId,spaceId,parentPageId,newTitle,newBlocks,revision+1,createdAt,now);}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
