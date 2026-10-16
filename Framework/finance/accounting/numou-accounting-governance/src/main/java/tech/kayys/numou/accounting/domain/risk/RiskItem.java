package tech.kayys.syirkah.accounting.domain.risk;

import java.util.Objects;

public final class RiskItem {
    public enum Level { LOW, MEDIUM, HIGH, CRITICAL }

    private final String riskId;
    private final String category;
    private final String title;
    private final Level inherentRisk;
    private Level residualRisk;

    public RiskItem(String riskId, String category, String title, Level inherentRisk) {
        this.riskId = Objects.requireNonNull(riskId, "riskId must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.inherentRisk = Objects.requireNonNull(inherentRisk, "inherentRisk must not be null");
        this.residualRisk = inherentRisk;
    }

    public void updateResidualRisk(Level residualRisk) {
        this.residualRisk = Objects.requireNonNull(residualRisk, "residualRisk must not be null");
    }

    public String riskId() { return riskId; }
    public String category() { return category; }
    public String title() { return title; }
    public Level inherentRisk() { return inherentRisk; }
    public Level residualRisk() { return residualRisk; }
}
