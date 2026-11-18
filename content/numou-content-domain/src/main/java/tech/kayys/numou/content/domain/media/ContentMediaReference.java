package tech.kayys.numou.content.domain.media;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable reference from one exact content revision to one tenant-owned media asset. */
public record ContentMediaReference(UUID id,TenantId tenantId,ContentId contentId,int revisionNumber,MediaAssetId mediaAssetId,String role,Instant createdAt) {
 public ContentMediaReference {
  Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(contentId);Objects.requireNonNull(mediaAssetId);Objects.requireNonNull(createdAt);
  if(revisionNumber<1)throw new IllegalArgumentException("revisionNumber must be positive");
  if(role==null||role.isBlank()||role.length()>64)throw new IllegalArgumentException("role must contain 1 to 64 characters");
 }
 public static ContentMediaReference create(TenantId tenant,ContentId content,int revision,MediaAssetId asset,String role,Instant now){return new ContentMediaReference(UUID.randomUUID(),tenant,content,revision,asset,role,now);}
}
