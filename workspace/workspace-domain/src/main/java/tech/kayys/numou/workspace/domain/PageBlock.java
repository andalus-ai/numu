package tech.kayys.numou.workspace.domain;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable typed content block; attributes are metadata, never executable code. */
public record PageBlock(UUID id, BlockType type, String text, int position, Map<String,String> attributes) {
 private static final Set<String> CALLOUT_TONES = Set.of("info", "success", "warning", "danger", "neutral");
 public PageBlock {
  Objects.requireNonNull(id, "id");
  Objects.requireNonNull(type, "type");
  if (position < 0) throw new IllegalArgumentException("position cannot be negative");
  text = text == null ? "" : text;
  attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  if (type == BlockType.DOMAIN_REFERENCE && !attributes.containsKey("referenceType"))
   throw new IllegalArgumentException("domain reference requires referenceType");
  if (type == BlockType.FILE && !attributes.containsKey("fileId"))
   throw new IllegalArgumentException("file block requires fileId");
  if (type == BlockType.CALLOUT && attributes.containsKey("tone") && !CALLOUT_TONES.contains(attributes.get("tone")))
   throw new IllegalArgumentException("callout tone must be info, success, warning, danger, or neutral");
  if (type == BlockType.CHECKLIST_ITEM && attributes.containsKey("checked")
      && !Set.of("true", "false").contains(attributes.get("checked")))
   throw new IllegalArgumentException("checklist checked attribute must be true or false");
 }
 public static PageBlock text(BlockType type, String text, int position) {
  if (type == BlockType.FILE || type == BlockType.DOMAIN_REFERENCE)
   throw new IllegalArgumentException("Typed attributes required for this block type");
  return new PageBlock(UUID.randomUUID(), type, text, position, Map.of());
 }
}
