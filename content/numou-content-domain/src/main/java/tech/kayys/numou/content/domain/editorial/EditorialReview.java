package tech.kayys.numou.content.domain.editorial;

import tech.kayys.numou.content.domain.ActorId;
import tech.kayys.numou.content.domain.ContentId;
import tech.kayys.numou.content.domain.TenantId;
import java.time.Instant;
import java.util.*;

/** Revision-bound editorial review aggregate. Decisions and comments are append-only. */
public final class EditorialReview {
    public enum Status { IN_REVIEW, APPROVED, CHANGES_REQUESTED, CANCELLED }
    public enum Decision { APPROVE, REQUEST_CHANGES }

    public record ReviewComment(UUID id, UUID authorId, String body, Instant createdAt) {
        public ReviewComment {
            Objects.requireNonNull(id); Objects.requireNonNull(authorId);
            if (body == null || body.isBlank()) throw new IllegalArgumentException("Comment body is required");
            body = body.trim(); Objects.requireNonNull(createdAt);
        }
    }
    public record ReviewDecision(UUID reviewerId, Decision decision, String comment, Instant decidedAt) {
        public ReviewDecision {
            Objects.requireNonNull(reviewerId); Objects.requireNonNull(decision); Objects.requireNonNull(decidedAt);
            if (decision == Decision.REQUEST_CHANGES && (comment == null || comment.isBlank()))
                throw new IllegalArgumentException("A change request must explain the required changes");
            comment = comment == null ? "" : comment.trim();
        }
    }

    private final EditorialReviewId id;
    private final TenantId tenantId;
    private final ContentId contentId;
    private final int revisionNumber;
    private final EditorialReviewPolicy policy;
    private final Status status;
    private final Map<UUID, ReviewDecision> decisions;
    private final List<ReviewComment> comments;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private EditorialReview(EditorialReviewId id, TenantId tenantId, ContentId contentId, int revisionNumber,
            EditorialReviewPolicy policy, Status status, Map<UUID, ReviewDecision> decisions,
            List<ReviewComment> comments, long version, Instant createdAt, Instant updatedAt) {
        this.id=Objects.requireNonNull(id); this.tenantId=Objects.requireNonNull(tenantId);
        this.contentId=Objects.requireNonNull(contentId); this.policy=Objects.requireNonNull(policy);
        if(revisionNumber<1 || version<0) throw new IllegalArgumentException("Invalid revision or aggregate version");
        this.revisionNumber=revisionNumber; this.status=Objects.requireNonNull(status);
        this.decisions=Map.copyOf(decisions); this.comments=List.copyOf(comments);
        this.version=version; this.createdAt=Objects.requireNonNull(createdAt); this.updatedAt=Objects.requireNonNull(updatedAt);
        if(!policy.reviewers().containsAll(this.decisions.keySet())) throw new IllegalArgumentException("Decision by an unassigned reviewer");
    }

    public static EditorialReview rehydrate(EditorialReviewId id, TenantId tenantId, ContentId contentId,
            int revisionNumber, EditorialReviewPolicy policy, Status status,
            Map<UUID, ReviewDecision> decisions, List<ReviewComment> comments,
            long version, Instant createdAt, Instant updatedAt) {
        return new EditorialReview(id, tenantId, contentId, revisionNumber, policy, status,
                decisions, comments, version, createdAt, updatedAt);
    }

    public static EditorialReview submit(TenantId tenantId, ContentId contentId, int revisionNumber,
            EditorialReviewPolicy policy, Instant now) {
        return new EditorialReview(EditorialReviewId.newId(), tenantId, contentId, revisionNumber, policy,
                Status.IN_REVIEW, Map.of(), List.of(), 0, now, now);
    }

    public EditorialReview decide(UUID reviewerId, Decision decision, String comment, Instant now) {
        requireOpen();
        if(!policy.reviewers().contains(reviewerId)) throw new IllegalArgumentException("Actor is not assigned to this review");
        if(decisions.containsKey(reviewerId)) throw new IllegalStateException("Reviewer has already submitted a decision");
        if(policy.mode()==EditorialReviewPolicy.Mode.SEQUENTIAL) {
            int next=decisions.size();
            if(!policy.reviewers().get(next).equals(reviewerId))
                throw new IllegalStateException("Sequential review must follow the configured reviewer order");
        }
        Map<UUID,ReviewDecision> next=new LinkedHashMap<>(decisions);
        next.put(reviewerId,new ReviewDecision(reviewerId,decision,comment,now));
        long approvals=next.values().stream().filter(d->d.decision()==Decision.APPROVE).count();
        boolean rejected=next.values().stream().anyMatch(d->d.decision()==Decision.REQUEST_CHANGES);
        Status nextStatus=rejected ? Status.CHANGES_REQUESTED
                : approvals>=policy.requiredApprovals() ? Status.APPROVED : Status.IN_REVIEW;
        return copy(nextStatus,next,comments,now);
    }

    public EditorialReview comment(ReviewComment comment, Instant now) {
        requireOpen();
        if(comments.stream().anyMatch(c->c.id().equals(comment.id()))) return this;
        List<ReviewComment> next=new ArrayList<>(comments); next.add(Objects.requireNonNull(comment));
        return copy(status,decisions,next,now);
    }

    public EditorialReview cancel(Instant now) {
        requireOpen();
        return copy(Status.CANCELLED,decisions,comments,now);
    }

    private void requireOpen() {
        if(status!=Status.IN_REVIEW) throw new IllegalStateException("Editorial review is already terminal: "+status);
    }
    private EditorialReview copy(Status s,Map<UUID,ReviewDecision>d,List<ReviewComment>c,Instant now) {
        return new EditorialReview(id,tenantId,contentId,revisionNumber,policy,s,d,c,version+1,createdAt,now);
    }
    public EditorialReviewId id(){return id;} public TenantId tenantId(){return tenantId;}
    public ContentId contentId(){return contentId;} public int revisionNumber(){return revisionNumber;}
    public EditorialReviewPolicy policy(){return policy;} public Status status(){return status;}
    public Map<UUID,ReviewDecision> decisions(){return decisions;} public List<ReviewComment> comments(){return comments;}
    public long version(){return version;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
}
