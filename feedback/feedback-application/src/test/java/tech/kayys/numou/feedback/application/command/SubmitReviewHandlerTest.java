package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.application.port.FeedbackReviewRepository;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.feedback.domain.review.FeedbackReview;
import tech.kayys.numou.feedback.domain.review.FeedbackReviewId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SubmitReviewHandlerTest {
    @Test
    void reviewMustReferenceRatingForSameTenantAndTarget() {
        TenantId tenant = TenantId.generate();
        RatingTarget target = new RatingTarget("product", "sku-1", "branch-1");
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(), tenant, target,
                RatingMetric.STAR_RATING, RatingScale.of(1, 5), BigDecimal.valueOf(5),
                new FeedbackActor("user", "u-1"), null, Instant.parse("2026-11-11T10:00:00Z"));
        AtomicReference<FeedbackReview> saved = new AtomicReference<>();
        FeedbackRatingRepository ratingRepository = new FeedbackRatingRepository() {
            @Override public FeedbackRatingId save(FeedbackRating value, String key) { return value.id(); }
            @Override public Optional<FeedbackRating> findById(TenantId tenantId, FeedbackRatingId id) {
                return tenant.equals(tenantId) && rating.id().equals(id) ? Optional.of(rating) : Optional.empty();
            }
            @Override public java.util.List<FeedbackRating> findByTarget(TenantId t, RatingTarget target, RatingMetric metric) {
                return java.util.List.of(rating);
            }
        };
        FeedbackReviewRepository reviewRepository = new FeedbackReviewRepository() {
            @Override public void save(FeedbackReview review) { saved.set(review); }
            @Override public void update(FeedbackReview review) { saved.set(review); }
            @Override public Optional<FeedbackReview> findById(TenantId t, FeedbackReviewId id) { return Optional.empty(); }
        };
        SubmitReviewHandler handler = new SubmitReviewHandler(ratingRepository, reviewRepository);
        FeedbackReviewId id = handler.handle(new SubmitReviewCommand(tenant, target,
                new FeedbackActor("user", "u-1"), rating.id(), "Good", "Fast delivery.",
                Instant.parse("2026-11-11T10:01:00Z")));
        assertEquals(id, saved.get().id());
        assertEquals(rating.id(), saved.get().ratingId());
    }

    @Test
    void rejectsNonStarRatingAsReviewScore() {
        TenantId tenant = TenantId.generate();
        RatingTarget target = new RatingTarget("service", "delivery-1", null);
        FeedbackRating nps = FeedbackRating.submit(FeedbackRatingId.generate(), tenant, target,
                RatingMetric.NPS, RatingScale.of(0, 10), BigDecimal.TEN,
                null, null, Instant.parse("2026-11-11T10:00:00Z"));
        FeedbackRatingRepository ratingRepository = new FeedbackRatingRepository() {
            @Override public FeedbackRatingId save(FeedbackRating value, String key) { return value.id(); }
            @Override public Optional<FeedbackRating> findById(TenantId t, FeedbackRatingId id) { return Optional.of(nps); }
            @Override public java.util.List<FeedbackRating> findByTarget(TenantId t, RatingTarget target, RatingMetric metric) {
                return java.util.List.of(nps);
            }
        };
        FeedbackReviewRepository reviewRepository = new FeedbackReviewRepository() {
            @Override public void save(FeedbackReview review) { fail("review must not be saved"); }
            @Override public void update(FeedbackReview review) { fail("review must not be updated"); }
            @Override public Optional<FeedbackReview> findById(TenantId t, FeedbackReviewId id) { return Optional.empty(); }
        };
        SubmitReviewHandler handler = new SubmitReviewHandler(ratingRepository, reviewRepository);
        assertThrows(IllegalArgumentException.class, () -> handler.handle(new SubmitReviewCommand(
                tenant, target, new FeedbackActor("user", "u-1"), nps.id(),
                "Not a star review", "Wrong metric", Instant.parse("2026-11-11T10:01:00Z"))));
    }

    @Test
    void rejectsRatingFromAnotherTarget() {
        TenantId tenant = TenantId.generate();
        RatingTarget rated = new RatingTarget("product", "sku-1", null);
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(), tenant, rated,
                RatingMetric.STAR_RATING, RatingScale.of(1, 5), BigDecimal.valueOf(4),
                null, null, Instant.parse("2026-11-11T10:00:00Z"));
        FeedbackRatingRepository ratingRepository = new FeedbackRatingRepository() {
            @Override public FeedbackRatingId save(FeedbackRating value, String key) { return value.id(); }
            @Override public Optional<FeedbackRating> findById(TenantId t, FeedbackRatingId id) { return Optional.of(rating); }
            @Override public java.util.List<FeedbackRating> findByTarget(TenantId t, RatingTarget target, RatingMetric metric) {
                return java.util.List.of(rating);
            }
        };
        FeedbackReviewRepository reviewRepository = new FeedbackReviewRepository() {
            @Override public void save(FeedbackReview review) { fail("review must not be saved"); }
            @Override public void update(FeedbackReview review) { fail("review must not be updated"); }
            @Override public Optional<FeedbackReview> findById(TenantId t, FeedbackReviewId id) { return Optional.empty(); }
        };
        SubmitReviewHandler handler = new SubmitReviewHandler(ratingRepository, reviewRepository);
        assertThrows(IllegalArgumentException.class, () -> handler.handle(new SubmitReviewCommand(
                tenant, new RatingTarget("product", "sku-2", null),
                new FeedbackActor("user", "u-1"), rating.id(), "Wrong", "Wrong target",
                Instant.parse("2026-11-11T10:01:00Z"))));
    }
}