package tech.kayys.syirkah.project.application.project;

import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Project profitability snapshot. */
public record ProjectProfitability(
        ProjectId projectId,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal grossMargin,
        BigDecimal marginPercentage
) {
    public ProjectProfitability {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(totalRevenue);
        Objects.requireNonNull(totalCost);
        Objects.requireNonNull(grossMargin);
        Objects.requireNonNull(marginPercentage);
    }

    public static ProjectProfitability compute(ProjectId id, BigDecimal revenue, BigDecimal cost) {
        BigDecimal margin = revenue.subtract(cost);
        BigDecimal pct = revenue.signum() > 0
                ? margin.divide(revenue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        return new ProjectProfitability(id, revenue, cost, margin, pct);
    }
}
