package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;

public class AuditUniverseScoringEngine {

    public record Inputs(
            int externalRiskScore,
            int nonConformanceCount,
            int overdueCompliance,
            double financialMateriality
    ) {
        public Inputs {
            if (externalRiskScore < 0) throw new AuditViolationException("externalRiskScore must be >= 0");
            if (nonConformanceCount < 0) throw new AuditViolationException("nonConformanceCount must be >= 0");
            if (overdueCompliance < 0) throw new AuditViolationException("overdueCompliance must be >= 0");
            if (financialMateriality < 0) throw new AuditViolationException("financialMateriality must be >= 0");
        }
    }

    public int score(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        int base = inputs.externalRiskScore() * 2;
        int ncFactor = Math.min(inputs.nonConformanceCount(), 10) * 2;
        int complianceFactor = Math.min(inputs.overdueCompliance(), 10) * 2;
        int materialityFactor = (int) Math.min(inputs.financialMateriality() / 1_000_000.0, 20);
        return Math.min(base + ncFactor + complianceFactor + materialityFactor, 100);
    }

    public RiskRating rating(int score) {
        if (score <= 25) return RiskRating.LOW;
        if (score <= 50) return RiskRating.MEDIUM;
        if (score <= 75) return RiskRating.HIGH;
        return RiskRating.CRITICAL;
    }
}
