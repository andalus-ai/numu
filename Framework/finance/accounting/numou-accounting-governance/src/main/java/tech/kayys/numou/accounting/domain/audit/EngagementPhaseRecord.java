package tech.kayys.syirkah.accounting.domain.audit;

import java.time.Instant;
import java.util.Objects;

public record EngagementPhaseRecord(
        AuditEngagement.Phase phase,
        Instant startedAt,
        Instant finishedAt,
        String actor
) {
    public EngagementPhaseRecord {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(actor, "actor");
    }
}
