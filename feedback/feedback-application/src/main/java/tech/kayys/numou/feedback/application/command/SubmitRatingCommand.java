package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.feedback.domain.rating.RatingScale;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;

public record SubmitRatingCommand(
        TenantId tenantId,
        RatingTarget target,
        RatingMetric metric,
        RatingScale scale,
        BigDecimal value,
        FeedbackActor respondent,
        String surveyReference,
        String idempotencyKey,
        Instant submittedAt) {}