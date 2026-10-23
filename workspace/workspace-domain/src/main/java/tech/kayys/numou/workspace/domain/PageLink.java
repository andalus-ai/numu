package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A tenant-scoped directed link between two workspace pages. It never embeds another domain's data. */
public record PageLink(UUID id, UUID tenantId, UUID sourcePageId, UUID targetPageId, UUID createdBy, String label, Instant createdAt) {
 public PageLink {
  Objects.requireNonNull(id, "id"); Objects.requireNonNull(tenantId, "tenantId");
  Objects.requireNonNull(sourcePageId, "sourcePageId"); Objects.requireNonNull(targetPageId, "targetPageId");
  Objects.requireNonNull(createdBy, "createdBy"); Objects.requireNonNull(createdAt, "createdAt");
  if(sourcePageId.equals(targetPageId)) throw new IllegalArgumentException("A page cannot link to itself");
  label=label==null?"":label.trim();
  if(label.length()>200) throw new IllegalArgumentException("Link label must be at most 200 characters");
 }
 public static PageLink create(UUID tenantId,UUID sourcePageId,UUID targetPageId,UUID actorId,String label,Instant now) {
  return new PageLink(UUID.randomUUID(),tenantId,sourcePageId,targetPageId,actorId,label,now);
 }
}
