package tech.kayys.numou.construction.application.consultancy;

import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementStatus;
import java.util.Objects;
import java.util.UUID;

/** Stable, codec-friendly response persisted for idempotent create retries. */
public record ConsultancyEngagementReceipt(
        UUID engagementId,
        String engagementNumber,
        ConsultancyEngagementStatus status,
        int scopeRevision) {
    public ConsultancyEngagementReceipt {
        Objects.requireNonNull(engagementId, "Engagement id is required");
        if (engagementNumber == null || engagementNumber.isBlank()) {
            throw new IllegalArgumentException("Engagement number is required");
        }
        Objects.requireNonNull(status, "Status is required");
        if (scopeRevision < 1) throw new IllegalArgumentException("Scope revision must be positive");
    }
}
