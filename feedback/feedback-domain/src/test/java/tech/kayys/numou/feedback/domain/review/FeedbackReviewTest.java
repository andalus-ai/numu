package tech.kayys.numou.feedback.domain.review;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackReviewTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void reviewStartsPendingAndCanBePublished() {
        FeedbackReview review = newReview();
        assertEquals(ReviewStatus.PENDING, review.status());
        assertEquals(VerificationStatus.UNVERIFIED, review.verificationStatus());
        review.verify(NOW.plusSeconds(1));
        review.publish(NOW.plusSeconds(2));
        assertEquals(ReviewStatus.PUBLISHED, review.status());
        assertEquals(VerificationStatus.VERIFIED, review.verificationStatus());
    }

    @Test
    void onlyPendingReviewsCanBePublishedOrRejected() {
        FeedbackReview review = newReview();
        review.reject(NOW.plusSeconds(1));
        assertEquals(ReviewStatus.REJECTED, review.status());
        assertThrows(InvalidStateException.class, () -> review.publish(NOW.plusSeconds(2)));
    }

    @Test
    void publishedReviewCanBeHidden() {
        FeedbackReview review = newReview();
        review.publish(NOW.plusSeconds(1));
        review.hide(NOW.plusSeconds(2));
        assertEquals(ReviewStatus.HIDDEN, review.status());
    }

    @Test
    void moderationCannotPrecedeSubmission() {
        FeedbackReview review = newReview();
        assertThrows(IllegalArgumentException.class, () -> review.publish(NOW.minusSeconds(1)));
    }

    private FeedbackReview newReview() {
        return FeedbackReview.submit(FeedbackReviewId.generate(), TenantId.generate(),
                new RatingTarget("product", "sku-123", "branch-1"),
                new FeedbackActor("user", "u-1"), FeedbackRatingId.generate(),
                "Great product", "Works as expected.", NOW);
    }
}