package tech.kayys.numou.content.domain;

import tech.kayys.numou.content.domain.model.ContentTypeId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContentEntry {
    private final ContentId id;
    private final TenantId tenantId;
    private final ContentStatus status;
    private final List<ContentRevision> revisions;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ContentEntry(ContentId id,TenantId tenantId,ContentStatus status,List<ContentRevision> revisions,
            Instant createdAt,Instant updatedAt,int persistedCurrentRevision) {
        this.id=Objects.requireNonNull(id);this.tenantId=Objects.requireNonNull(tenantId);
        this.status=Objects.requireNonNull(status);this.revisions=List.copyOf(revisions);
        this.createdAt=Objects.requireNonNull(createdAt);this.updatedAt=Objects.requireNonNull(updatedAt);
        if(this.revisions.isEmpty())throw new IllegalArgumentException("Content entry must contain at least one revision");
        for(int i=0;i<this.revisions.size();i++)
            if(this.revisions.get(i).number()!=i+1)throw new IllegalArgumentException("Revision sequence must be contiguous");
        if(persistedCurrentRevision!=this.revisions.size())
            throw new IllegalArgumentException("Persisted current revision pointer does not match revision history");
    }

    public static ContentEntry create(TenantId tenantId,String title,String slug,String body,
            ContentTypeId typeId,int schemaVersion,ActorId authorId,Instant now) {
        Objects.requireNonNull(typeId,"typeId");
        if(schemaVersion<1)throw new IllegalArgumentException("Schema version must be positive");
        ContentRevision first=new ContentRevision(1,title,slug,body,typeId,schemaVersion,authorId,now);
        return new ContentEntry(ContentId.newId(),tenantId,ContentStatus.DRAFT,List.of(first),now,now,1);
    }

    /** Rehydrates legacy rows too; null schema identity is reserved for pre-M3 history. */
    public static ContentEntry rehydrate(ContentId id,TenantId tenantId,ContentStatus status,List<ContentRevision> revisions,
            Instant createdAt,Instant updatedAt,int persistedCurrentRevision) {
        return new ContentEntry(id,tenantId,status,revisions,createdAt,updatedAt,persistedCurrentRevision);
    }

    public ContentEntry revise(int expectedRevision,String title,String slug,String body,ContentTypeId typeId,
            int schemaVersion,ActorId authorId,Instant now) {
        if(status!=ContentStatus.DRAFT)throw new IllegalStateException("Only draft content can be revised");
        if(expectedRevision!=currentRevisionNumber())
            throw new RevisionConflictException(expectedRevision,currentRevisionNumber());
        Objects.requireNonNull(typeId,"typeId");
        if(schemaVersion<1)throw new IllegalArgumentException("Schema version must be positive");
        List<ContentRevision> next=new ArrayList<>(revisions);
        next.add(new ContentRevision(expectedRevision+1,title,slug,body,typeId,schemaVersion,authorId,now));
        return new ContentEntry(id,tenantId,status,next,createdAt,now,expectedRevision+1);
    }

    public ContentEntry transitionTo(ContentStatus target,Instant now) {
        Objects.requireNonNull(target,"target status");Objects.requireNonNull(now,"transition time");
        if(!ContentLifecyclePolicy.canTransition(status,target))
            throw new IllegalStateException("Invalid content lifecycle transition: "+status+" -> "+target);
        return new ContentEntry(id,tenantId,target,revisions,createdAt,now,currentRevisionNumber());
    }
    public ContentEntry archive(Instant now){return transitionTo(ContentStatus.ARCHIVED,now);}
    public ContentEntry restore(Instant now){return transitionTo(ContentStatus.DRAFT,now);}
    public int currentRevisionNumber(){return revisions.get(revisions.size()-1).number();}
    public ContentRevision currentRevision(){return revisions.get(revisions.size()-1);}
    public ContentId id(){return id;} public TenantId tenantId(){return tenantId;} public ContentStatus status(){return status;}
    public List<ContentRevision> revisions(){return revisions;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}
