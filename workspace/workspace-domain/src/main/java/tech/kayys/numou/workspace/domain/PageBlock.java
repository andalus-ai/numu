package tech.kayys.numou.workspace.domain;
import java.util.Map; import java.util.Objects; import java.util.UUID;
/** Immutable typed content block; attributes are metadata, never executable code. */
public record PageBlock(UUID id,BlockType type,String text,int position,Map<String,String> attributes) {
 public PageBlock { Objects.requireNonNull(id);Objects.requireNonNull(type);if(position<0)throw new IllegalArgumentException("position cannot be negative");text=text==null?"":text;attributes=attributes==null?Map.of():Map.copyOf(attributes);if(type==BlockType.DOMAIN_REFERENCE&&!attributes.containsKey("referenceType"))throw new IllegalArgumentException("domain reference requires referenceType");if(type==BlockType.FILE&&!attributes.containsKey("fileId"))throw new IllegalArgumentException("file block requires fileId"); }
 public static PageBlock text(BlockType type,String text,int position){if(type==BlockType.FILE||type==BlockType.DOMAIN_REFERENCE)throw new IllegalArgumentException("Typed attributes required for this block type");return new PageBlock(UUID.randomUUID(),type,text,position,Map.of());}
}
