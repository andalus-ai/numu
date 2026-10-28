package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, replay-identifiable attribution result with enough policy evidence to explain the decision. */
public record AttributionDecision(
        UUID id,
        String tenantId,
        UUID programId,
        String sourceType,
        String sourceId,
        String conversionEventId,
        AttributionOutcome outcome,
        UUID partnerId,
        UUID touchpointId,
        UUID policyId,
        long policyVersion,
        Instant conversionAt,
        Instant decidedAt) {

    public AttributionDecision {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        conversionEventId = required(conversionEventId, "conversionEventId");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(policyId, "policyId is required");
        if (policyVersion < 1) throw new IllegalArgumentException("policyVersion must be positive");
        Objects.requireNonNull(conversionAt, "conversionAt is required");
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        if (outcome == AttributionOutcome.ATTRIBUTED && (partnerId == null || touchpointId == null)) {
            throw new IllegalArgumentException("attributed decisions require partner and touchpoint");
        }
        if (outcome == AttributionOutcome.UNATTRIBUTED && (partnerId != null || touchpointId != null)) {
            throw new IllegalArgumentException("unattributed decisions cannot identify a partner or touchpoint");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
