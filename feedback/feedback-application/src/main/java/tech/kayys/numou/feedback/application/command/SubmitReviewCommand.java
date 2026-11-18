package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

public record SubmitReviewCommand(
        TenantId tenantId,
        RatingTarget target,
        FeedbackActor author,
        FeedbackRatingId ratingId,
        String title,
        String body,
        Instant createdAt) {}