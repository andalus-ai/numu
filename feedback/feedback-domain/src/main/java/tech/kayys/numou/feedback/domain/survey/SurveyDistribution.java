package tech.kayys.numou.feedback.domain.survey;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One tenant-scoped invitation/distribution grant. Only a one-way token hash is
 * persisted; the raw bearer token is returned to the caller once at creation.
 */
public record SurveyDistribution(
        UUID id,
        TenantId tenantId,
        String surveyId,
        DistributionChannel channel,
        String tokenHash,
        Instant expiresAt,
        int maxResponses,
        DistributionStatus status,
        Instant createdAt) {

    public SurveyDistribution {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (surveyId == null || surveyId.isBlank() || surveyId.trim().length() > 200)
            throw new IllegalArgumentException("surveyId must contain 1..200 characters");
        surveyId = surveyId.trim();
        Objects.requireNonNull(channel, "channel cannot be null");
        if (tokenHash == null || !tokenHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex digest");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must be after createdAt");
        if (maxResponses < 1 || maxResponses > 1_000_000)
            throw new IllegalArgumentException("maxResponses must be in 1..1000000");
        Objects.requireNonNull(status, "status cannot be null");
    }

    public boolean acceptsAt(Instant now, int acceptedResponses) {
        return status == DistributionStatus.ACTIVE && now.isBefore(expiresAt)
                && acceptedResponses < maxResponses;
    }
}
