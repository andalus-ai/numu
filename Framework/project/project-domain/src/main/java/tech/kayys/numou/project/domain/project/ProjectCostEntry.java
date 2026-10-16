package tech.kayys.syirkah.project.domain.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cost transaction entry charged to a specific project. */
public record ProjectCostEntry(
        String id,
        ProjectId projectId,
        String costCategory,
        BigDecimal amount,
        String currency,
        String sourceReference,
        Instant occurredAt
) {
    public ProjectCostEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(costCategory);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Cost amount must be positive");
    }
    public static ProjectCostEntry of(ProjectId projId, String cat, BigDecimal amt, String curr, String ref) {
        return new ProjectCostEntry(UUID.randomUUID().toString(), projId, cat, amt, curr, ref, Instant.now());
    }
}
