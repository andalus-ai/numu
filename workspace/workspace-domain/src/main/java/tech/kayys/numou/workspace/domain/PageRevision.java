package tech.kayys.numou.workspace.domain;
import java.time.Instant; import java.util.List; import java.util.Objects; import java.util.UUID;
/** Immutable snapshot for revision history and future restore operations. */
public record PageRevision(UUID tenantId,UUID pageId,long revision,String title,List<PageBlock> blocks,UUID actorId,Instant recordedAt,String changeSummary) {
 public PageRevision { Objects.requireNonNull(tenantId);Objects.requireNonNull(pageId);if(revision<1)throw new IllegalArgumentException("revision must be positive");if(title==null||title.isBlank())throw new IllegalArgumentException("title is required");title=title.trim();blocks=blocks==null?List.of():List.copyOf(blocks);Objects.requireNonNull(actorId);Objects.requireNonNull(recordedAt);changeSummary=changeSummary==null?"":changeSummary.trim(); }
 public static PageRevision snapshot(Page p,UUID actor,Instant now,String summary){return new PageRevision(p.tenantId(),p.id(),p.revision(),p.title(),p.blocks(),actor,now,summary);}
}
