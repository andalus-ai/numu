package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit entry for a schedule-change governance decision. */
public record ScheduleChangeDecision(UUID decisionId, UUID actorId, Outcome outcome,
        Instant decidedAt, String rationale, long livePlanRevision) {
    public ScheduleChangeDecision {
        Objects.requireNonNull(decisionId, "Decision id cannot be null");
        Objects.requireNonNull(actorId, "Decision actor cannot be null");
        Objects.requireNonNull(outcome, "Decision outcome cannot be null");
        Objects.requireNonNull(decidedAt, "Decision timestamp cannot be null");
        rationale = Objects.requireNonNull(rationale, "Decision rationale cannot be null").trim();
        if (rationale.isEmpty()) throw new IllegalArgumentException("Decision rationale cannot be blank");
        if (livePlanRevision < 0) throw new IllegalArgumentException("Live plan revision cannot be negative");
    }
    public enum Outcome { APPROVE, REJECT, MARK_STALE, SUPERSEDE }
}
