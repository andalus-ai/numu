package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.ImpactLevel;
import tech.kayys.numou.project.domain.risk.Probability;
import tech.kayys.numou.project.domain.risk.RiskCategory;
import tech.kayys.numou.project.domain.risk.RiskId;
import tech.kayys.numou.project.domain.risk.RiskResponse;
import tech.kayys.numou.project.domain.risk.RiskStatus;

import java.time.LocalDate;
import java.util.Objects;

/** One row of the risk register read model. */
public record RiskRegisterRow(
        RiskId riskId,
        ProjectId projectId,
        String number,
        String title,
        RiskCategory category,
        RiskStatus status,
        Probability probability,
        ImpactLevel impact,
        int score,
        RiskResponse response,
        LocalDate targetDate
) {

    public RiskRegisterRow {
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }
}
