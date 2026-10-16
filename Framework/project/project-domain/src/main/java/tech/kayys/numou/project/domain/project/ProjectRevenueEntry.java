package tech.kayys.syirkah.project.domain.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Recognized revenue entry linked to a project. */
public record ProjectRevenueEntry(
        String id,
        ProjectId projectId,
        String milestone,
        BigDecimal amount,
        String currency,
        String invoiceReference,
        Instant occurredAt
) {
    public ProjectRevenueEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(milestone);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(occurredAt);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Revenue amount must be positive");
    }
    public static ProjectRevenueEntry of(ProjectId projId, String milestone, BigDecimal amt, String curr, String invRef) {
        return new ProjectRevenueEntry(UUID.randomUUID().toString(), projId, milestone, amt, curr, invRef, Instant.now());
    }
}
