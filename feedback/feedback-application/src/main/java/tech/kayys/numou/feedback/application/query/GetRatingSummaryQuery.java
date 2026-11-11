package tech.kayys.numou.feedback.application.query;

import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.feedback.domain.rating.RatingTarget;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record GetRatingSummaryQuery(TenantId tenantId, RatingTarget target, RatingMetric metric) {}