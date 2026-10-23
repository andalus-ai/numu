package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only human governance decision about an immutable progress snapshot. */
public record ProgressSnapshotDecision(UUID decisionId, UUID tenantId, UUID snapshotId, UUID actorId,
        Outcome outcome, String reason, Instant decidedAt) {
    public enum Outcome { APPROVED, REJECTED }
    public ProgressSnapshotDecision {
        Objects.requireNonNull(decisionId, "Decision id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(snapshotId, "Snapshot id cannot be null");
        Objects.requireNonNull(actorId, "Actor id cannot be null");
        Objects.requireNonNull(outcome, "Outcome cannot be null");
        Objects.requireNonNull(reason, "Reason is required for an auditable decision");
        reason = reason.trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("Reason is required for an auditable decision");
        if (reason.length() > 2000) throw new IllegalArgumentException("Reason must not exceed 2000 characters");
        Objects.requireNonNull(decidedAt, "Decision timestamp cannot be null");
    }
}
