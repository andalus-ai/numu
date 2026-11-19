package tech.kayys.numou.content.application;

import tech.kayys.numou.content.domain.*;
import tech.kayys.numou.content.domain.editorial.*;
import tech.kayys.numou.content.spi.ContentRepository;
import tech.kayys.numou.content.spi.EditorialAuthorizationPort;
import tech.kayys.numou.content.spi.EditorialReviewRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** CMS owns editorial decisions; a durable outbox drives optional Gamelan orchestration. */
public final class EditorialReviewApplicationService {
    private final ContentRepository content;
    private final EditorialReviewRepository reviews;
    private final EditorialAuthorizationPort authorization;
    private final Clock clock;

    public EditorialReviewApplicationService(ContentRepository content, EditorialReviewRepository reviews,
            EditorialAuthorizationPort authorization, Clock clock) {
        this.content=Objects.requireNonNull(content); this.reviews=Objects.requireNonNull(reviews);
        this.authorization=Objects.requireNonNull(authorization); this.clock=Objects.requireNonNull(clock);
    }

    public EditorialReview submit(ContentCommandContext context, ContentId contentId, EditorialReviewPolicy policy) {
        authorization.requireSubmit(context.tenantId(), context.actorId(), contentId);
        ContentEntry entry=content.find(context.tenantId(),contentId).orElseThrow(ContentNotFoundException::new);
        if(entry.status()!=ContentStatus.DRAFT) throw new IllegalStateException("Only draft content can be submitted for review");
        for(UUID reviewer:policy.reviewers())
            authorization.requireReviewerEligible(context.tenantId(),context.actorId(),reviewer);
        EditorialReview review=EditorialReview.submit(context.tenantId(),contentId,entry.currentRevisionNumber(),policy,clock.instant());
        reviews.submit(review);
        return review;
    }

    public EditorialReview decide(ContentCommandContext context, EditorialReviewId reviewId,
            EditorialReview.Decision decision, String comment) {
        EditorialReview current=reviews.find(context.tenantId(),reviewId).orElseThrow(ContentNotFoundException::new);
        authorization.requireReview(context.tenantId(),context.actorId(),current.contentId());
        EditorialReview changed=current.decide(context.actorId().value(),decision,comment,clock.instant());
        if(!reviews.update(changed,current.version()))
            throw new IllegalStateException("Editorial review changed concurrently; reload before retrying");
        return changed;
    }

    public EditorialReview comment(ContentCommandContext context, EditorialReviewId reviewId, UUID commentId, String body) {
        EditorialReview current=reviews.find(context.tenantId(),reviewId).orElseThrow(ContentNotFoundException::new);
        authorization.requireComment(context.tenantId(),context.actorId(),current.contentId());
        EditorialReview changed=current.comment(new EditorialReview.ReviewComment(
                commentId,context.actorId().value(),body,clock.instant()),clock.instant());
        if(changed==current) return current;
        if(!reviews.update(changed,current.version()))
            throw new IllegalStateException("Editorial review changed concurrently; reload before retrying");
        return changed;
    }

    public EditorialReview cancel(ContentCommandContext context, EditorialReviewId reviewId) {
        EditorialReview current=reviews.find(context.tenantId(),reviewId).orElseThrow(ContentNotFoundException::new);
        authorization.requireSubmit(context.tenantId(),context.actorId(),current.contentId());
        EditorialReview cancelled=current.cancel(clock.instant());
        if(!reviews.update(cancelled,current.version()))
            throw new IllegalStateException("Editorial review changed concurrently; reload before retrying");
        return cancelled;
    }

    public EditorialReview get(ContentCommandContext context, EditorialReviewId reviewId) {
        return reviews.find(context.tenantId(),reviewId).orElseThrow(ContentNotFoundException::new);
    }
}
