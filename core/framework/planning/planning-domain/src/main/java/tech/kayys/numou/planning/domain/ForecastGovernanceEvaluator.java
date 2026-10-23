package tech.kayys.numou.planning.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates whether WBS schedule-risk summaries are suitable for a report date.
 * This is a governance check, not a schedule recalculation or approval workflow.
 */
public final class ForecastGovernanceEvaluator {
    public ForecastGovernanceReport evaluate(UUID tenantId, UUID structureId, PlanId planId,
            LocalDate reportDate, int maximumEvidenceAgeDays, Set<String> supportedMethodVersions,
            List<WorkBreakdownScheduleRisk> risks) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(reportDate, "Report date cannot be null");
        Objects.requireNonNull(supportedMethodVersions, "Supported method versions cannot be null");
        Objects.requireNonNull(risks, "Schedule risks cannot be null");
        if (maximumEvidenceAgeDays < 0) throw new IllegalArgumentException("Maximum evidence age cannot be negative");

        Set<String> supported = new HashSet<>();
        for (String version : supportedMethodVersions) {
            Objects.requireNonNull(version, "Supported method version cannot be null");
            String normalized = version.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException("Supported method version cannot be blank");
            supported.add(normalized);
        }

        List<ForecastGovernanceFinding> findings = new ArrayList<>();
        Set<UUID> seenNodes = new HashSet<>();
        for (WorkBreakdownScheduleRisk risk : risks) {
            Objects.requireNonNull(risk, "Schedule risk cannot be null");
            UUID nodeId = risk.nodeId();
            if (!seenNodes.add(nodeId)) findings.add(new ForecastGovernanceFinding(nodeId,
                    ForecastGovernanceFinding.Code.DUPLICATE_WBS_NODE, ForecastGovernanceFinding.Severity.ERROR,
                    "More than one schedule-risk summary was supplied for this WBS node."));
            if (!tenantId.equals(risk.tenantId()) || !structureId.equals(risk.structureId())
                    || !planId.equals(risk.planId())) {
                findings.add(new ForecastGovernanceFinding(nodeId, ForecastGovernanceFinding.Code.SCOPE_MISMATCH,
                        ForecastGovernanceFinding.Severity.ERROR,
                        "Schedule-risk summary does not match the requested tenant, structure, and plan."));
                continue;
            }
            if (risk.progressForecast().isEmpty()) {
                findings.add(new ForecastGovernanceFinding(nodeId,
                        ForecastGovernanceFinding.Code.MISSING_PROGRESS_EVIDENCE,
                        ForecastGovernanceFinding.Severity.INFO,
                        "No progress-trend forecast was supplied; schedule dates remain dependency-derived."));
                continue;
            }
            ProgressForecast forecast = risk.progressForecast().orElseThrow();
            long age = ChronoUnit.DAYS.between(forecast.latestAsOfDate(), reportDate);
            if (age < 0) findings.add(new ForecastGovernanceFinding(nodeId,
                    ForecastGovernanceFinding.Code.FUTURE_PROGRESS_EVIDENCE, ForecastGovernanceFinding.Severity.ERROR,
                    "Latest progress evidence is dated after the report date."));
            else if (age > maximumEvidenceAgeDays) findings.add(new ForecastGovernanceFinding(nodeId,
                    ForecastGovernanceFinding.Code.STALE_PROGRESS_EVIDENCE, ForecastGovernanceFinding.Severity.WARNING,
                    "Latest progress evidence is " + age + " days old; the configured maximum is "
                            + maximumEvidenceAgeDays + " days."));
            if (!supported.contains(forecast.methodVersion())) findings.add(new ForecastGovernanceFinding(nodeId,
                    ForecastGovernanceFinding.Code.UNSUPPORTED_FORECAST_METHOD, ForecastGovernanceFinding.Severity.WARNING,
                    "Forecast method version '" + forecast.methodVersion()
                            + "' is not in the report's supported method-version set."));
        }
        findings.sort(Comparator.comparing((ForecastGovernanceFinding f) -> f.nodeId().toString())
                .thenComparing(f -> f.code().name()));
        return new ForecastGovernanceReport(tenantId, structureId, planId, reportDate,
                maximumEvidenceAgeDays, findings);
    }
}
