package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.project.domain.project.ProjectId;

import java.util.Objects;

/** Aggregated counters for the risk dashboard. */
public record ProjectRiskSummary(
        ProjectId projectId,
        long totalRisks,
        long openRisks,
        long highRisks,
        long criticalRisks,
        long materializedRisks,
        long openIssues,
        long criticalIssues,
        long overdueActions
) {

    public ProjectRiskSummary {
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }
}
