package tech.kayys.numou.content.domain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
public final class ContentEntry {
 private final ContentId id; private final TenantId tenantId; private final ContentStatus status;
 private final List<ContentRevision> revisions; private final Instant createdAt; private final Instant updatedAt;
 private ContentEntry(ContentId id, TenantId tenantId, ContentStatus status, List<ContentRevision> revisions, Instant createdAt, Instant updatedAt) {
  this.id=Objects.requireNonNull(id); this.tenantId=Objects.requireNonNull(tenantId); this.status=Objects.requireNonNull(status);
  this.revisions=List.copyOf(revisions); this.createdAt=Objects.requireNonNull(createdAt); this.updatedAt=Objects.requireNonNull(updatedAt);
  if (this.revisions.isEmpty()) throw new IllegalArgumentException("Content entry must contain at least one revision");
  for (int i=0;i<this.revisions.size();i++) if (this.revisions.get(i).number()!=i+1) throw new IllegalArgumentException("Revision sequence must be contiguous");
 }
 public static ContentEntry create(TenantId tenantId, String title, String slug, String body, ActorId authorId, Instant now) {
  Objects.requireNonNull(now); ContentRevision first=new ContentRevision(1,title,slug,body,authorId,now);
  return new ContentEntry(ContentId.newId(),tenantId,ContentStatus.DRAFT,List.of(first),now,now);
 }
 public static ContentEntry rehydrate(ContentId id,TenantId tenantId,ContentStatus status,List<ContentRevision> revisions,Instant createdAt,Instant updatedAt) {
  return new ContentEntry(id,tenantId,status,revisions,createdAt,updatedAt);
 }
 public ContentEntry revise(int expectedRevision,String title,String slug,String body,ActorId authorId,Instant now) {
  if(status!=ContentStatus.DRAFT) throw new IllegalStateException("Only draft content can be revised");
  if(expectedRevision!=currentRevisionNumber()) throw new RevisionConflictException(expectedRevision,currentRevisionNumber());
  List<ContentRevision> next=new ArrayList<>(revisions);
  next.add(new ContentRevision(expectedRevision+1,title,slug,body,authorId,now));
  return new ContentEntry(id,tenantId,status,next,createdAt,now);
 }
 public ContentEntry archive(Instant now) {
  if(status==ContentStatus.ARCHIVED) return this;
  return new ContentEntry(id,tenantId,ContentStatus.ARCHIVED,revisions,createdAt,now);
 }
 public ContentEntry restore(Instant now) {
  if(status!=ContentStatus.ARCHIVED) throw new IllegalStateException("Only archived content can be restored");
  return new ContentEntry(id,tenantId,ContentStatus.DRAFT,revisions,createdAt,now);
 }
 public int currentRevisionNumber(){return revisions.get(revisions.size()-1).number();}
 public ContentRevision currentRevision(){return revisions.get(revisions.size()-1);}
 public ContentId id(){return id;} public TenantId tenantId(){return tenantId;} public ContentStatus status(){return status;}
 public List<ContentRevision> revisions(){return revisions;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}