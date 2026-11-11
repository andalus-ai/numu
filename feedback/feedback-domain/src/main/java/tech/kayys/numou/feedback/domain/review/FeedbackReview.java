package tech.kayys.numou.feedback.domain.review;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Moderated review content linked to an independently persisted rating response. */
public final class FeedbackReview {
    private final FeedbackReviewId id;
    private final TenantId tenantId;
    private final RatingTarget target;
    private final FeedbackActor author;
    private final FeedbackRatingId ratingId;
    private final String title;
    private final String body;
    private final Instant createdAt;
    private ReviewStatus status;
    private VerificationStatus verificationStatus;
    private Instant moderatedAt;

    private FeedbackReview(FeedbackReviewId id, TenantId tenantId, RatingTarget target,
                           FeedbackActor author, FeedbackRatingId ratingId, String title,
                           String body, VerificationStatus verificationStatus, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.author = Objects.requireNonNull(author, "author cannot be null");
        this.ratingId = Objects.requireNonNull(ratingId, "ratingId cannot be null");
        this.title = requireText(title, "title", 200);
        this.body = requireText(body, "body", 20_000);
        this.verificationStatus = Objects.requireNonNull(verificationStatus, "verificationStatus cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.status = ReviewStatus.PENDING;
    }

    public static FeedbackReview submit(FeedbackReviewId id, TenantId tenantId, RatingTarget target,
                                        FeedbackActor author, FeedbackRatingId ratingId, String title,
                                        String body, Instant createdAt) {
        return new FeedbackReview(id, tenantId, target, author, ratingId, title, body,
                VerificationStatus.UNVERIFIED, createdAt);
    }

    /** Only a trusted commerce/service adapter should assert verification. */
    public void verify(Instant at) {
        ensurePending();
        verificationStatus = VerificationStatus.VERIFIED;
        moderatedAt = requireModerationTime(at);
    }

    public void publish(Instant at) {
        ensurePending();
        moderatedAt = requireModerationTime(at);
        status = ReviewStatus.PUBLISHED;
    }

    public void reject(Instant at) {
        ensurePending();
        moderatedAt = requireModerationTime(at);
        status = ReviewStatus.REJECTED;
    }

    public void hide(Instant at) {
        if (status != ReviewStatus.PUBLISHED) throw new InvalidStateException("Only published reviews can be hidden");
        moderatedAt = requireModerationTime(at);
        status = ReviewStatus.HIDDEN;
    }

    private void ensurePending() {
        if (status != ReviewStatus.PENDING) throw new InvalidStateException("Only pending reviews can be moderated");
    }

    private Instant requireModerationTime(Instant at) {
        Instant value = Objects.requireNonNull(at, "moderation time cannot be null");
        if (value.isBefore(createdAt)) throw new IllegalArgumentException("moderation time cannot precede creation");
        return value;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    public FeedbackReviewId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public RatingTarget target() { return target; }
    public FeedbackActor author() { return author; }
    public FeedbackRatingId ratingId() { return ratingId; }
    public String title() { return title; }
    public String body() { return body; }
    public Instant createdAt() { return createdAt; }
    public ReviewStatus status() { return status; }
    public VerificationStatus verificationStatus() { return verificationStatus; }
    public Instant moderatedAt() { return moderatedAt; }
}