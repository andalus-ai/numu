package tech.kayys.numou.affiliate.domain;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
/** Pure attribution policy evaluator. Persistence, source verification, and replay protection are external. */
public final class AttributionEngine {
    private AttributionEngine() {}
    public static AttributionDecision decide(UUID decisionId, String tenantId, AttributionPolicy policy,
            ConversionReference conversion, List<AffiliateTouchpoint> touchpoints, Instant decidedAt) {
        Objects.requireNonNull(decisionId, "decisionId is required");
        String tenant = required(tenantId, "tenantId");
        Objects.requireNonNull(policy, "policy is required");
        Objects.requireNonNull(conversion, "conversion is required");
        Objects.requireNonNull(touchpoints, "touchpoints is required");
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        if (!policy.tenantId().equals(tenant)) throw new IllegalArgumentException("policy tenant mismatch");
        if (!policy.isEffectiveAt(conversion.occurredAt())) throw new IllegalArgumentException("policy is not effective for conversion timestamp");
        if (decidedAt.isBefore(conversion.occurredAt())) throw new IllegalArgumentException("decision time precedes conversion time");
        Instant earliest;
        try { earliest = conversion.occurredAt().minusSeconds(policy.windowSeconds()); }
        catch (java.time.DateTimeException | ArithmeticException ex) { earliest = Instant.MIN; }
        Comparator<AffiliateTouchpoint> chronological = Comparator.comparing(AffiliateTouchpoint::capturedAt)
                .thenComparing(t -> t.id().toString());
        List<AffiliateTouchpoint> eligible = touchpoints.stream()
                .filter(t -> t.tenantId().equals(tenant))
                .filter(t -> t.programId().equals(policy.programId()))
                .filter(t -> t.subjectKey().equals(conversion.subjectKey()))
                .filter(t -> !t.capturedAt().isAfter(conversion.occurredAt()))
                .filter(t -> !t.capturedAt().isBefore(earliest))
                .toList();
        Comparator<AffiliateTouchpoint> selection = policy.model() == AttributionModel.FIRST_TOUCH
                ? chronological : chronological.reversed();
        AffiliateTouchpoint selected = eligible.stream().min(selection).orElse(null);
        return new AttributionDecision(decisionId, tenant, policy.programId(), conversion.sourceType(),
                conversion.sourceId(), conversion.eventId(),
                selected == null ? AttributionOutcome.UNATTRIBUTED : AttributionOutcome.ATTRIBUTED,
                selected == null ? null : selected.partnerId(), selected == null ? null : selected.id(),
                policy.id(), policy.version(), conversion.occurredAt(), decidedAt);
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String v = value.trim();
        if (v.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}
