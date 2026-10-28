package tech.kayys.numou.affiliate.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure deterministic attribution logic. Persistence, source verification, and replay protection live outside the domain. */
public final class AttributionEngine {
    private AttributionEngine() {
    }

    public static AttributionDecision decide(
            UUID decisionId,
            String tenantId,
            AttributionPolicy policy,
            ConversionReference conversion,
            List<AffiliateTouchpoint> touchpoints,
            Instant decidedAt) {
        Objects.requireNonNull(decisionId, "decisionId is required");
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(conversion, "conversion is required");
        Objects.requireNonNull(touchpoints, "touchpoints is required");
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        String normalizedTenant = required(tenantId, "tenantId");

        if (!policy.tenantId().equals(normalizedTenant)) {
            throw new IllegalArgumentException("policy tenant does not match request tenant");
        }
        if (!policy.isEffectiveAt(conversion.occurredAt())) {
            throw new IllegalArgumentException("no effective attribution policy for conversion timestamp");
        }
        if (decidedAt.isBefore(conversion.occurredAt())) {
            throw new IllegalArgumentException("decision time cannot precede conversion time");
        }

        Instant earliest = conversion.occurredAt().minusSeconds(policy.windowSeconds());
        Comparator<AffiliateTouchpoint> byTimeThenId =
                Comparator.comparing(AffiliateTouchpoint::capturedAt)
                        .thenComparing(t -> t.id().toString());

        List<AffiliateTouchpoint> eligible = touchpoints.stream()
                .filter(t -> t.tenantId().equals(normalizedTenant))
                .filter(t -> t.programId().equals(policy.programId()))
                .filter(t -> t.subjectKey().equals(conversion.subjectKey()))
                .filter(t -> !t.capturedAt().isAfter(conversion.occurredAt()))
                .filter(t -> !t.capturedAt().isBefore(earliest))
                .toList();

        Comparator<AffiliateTouchpoint> selection = policy.model() == AttributionModel.FIRST_TOUCH
                ? byTimeThenId
                : byTimeThenId.reversed();

        AffiliateTouchpoint selected = eligible.stream().min(selection).orElse(null);
        AttributionOutcome outcome = selected == null
                ? AttributionOutcome.UNATTRIBUTED : AttributionOutcome.ATTRIBUTED;

        return new AttributionDecision(
                decisionId,
                normalizedTenant,
                policy.programId(),
                conversion.sourceType(),
                conversion.sourceId(),
                conversion.eventId(),
                outcome,
                selected == null ? null : selected.partnerId(),
                selected == null ? null : selected.id(),
                policy.id(),
                policy.version(),
                conversion.occurredAt(),
                decidedAt);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
