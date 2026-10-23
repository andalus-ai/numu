package tech.kayys.numou.planning.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Auditable outcome of checking forecast evidence against a reporting context. */
public record ForecastGovernanceReport(UUID tenantId, UUID structureId, PlanId planId,
        LocalDate reportDate, int maximumEvidenceAgeDays, List<ForecastGovernanceFinding> findings) {
    public ForecastGovernanceReport {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(reportDate, "Report date cannot be null");
        findings = List.copyOf(Objects.requireNonNull(findings, "Findings cannot be null"));
        if (maximumEvidenceAgeDays < 0) throw new IllegalArgumentException("Maximum evidence age cannot be negative");
    }
    public boolean hasErrors() {
        return findings.stream().anyMatch(f -> f.severity() == ForecastGovernanceFinding.Severity.ERROR);
    }
    public boolean hasWarnings() {
        return findings.stream().anyMatch(f -> f.severity() == ForecastGovernanceFinding.Severity.WARNING);
    }
    public long count(ForecastGovernanceFinding.Code code) {
        Objects.requireNonNull(code, "Finding code cannot be null");
        return findings.stream().filter(f -> f.code() == code).count();
    }
}
