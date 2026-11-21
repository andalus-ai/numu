package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.survey.DistributionChannel;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

public record CreateSurveyDistributionCommand(
        TenantId tenantId, String surveyId, DistributionChannel channel,
        Instant expiresAt, int maxResponses, Instant createdAt) { }
