package tech.kayys.syirkah.project.domain.risk;

/**
 * Calculated score, never hand-entered: {@code score = probability ×
 * impact}, which makes states like {@code 5 × 5 = 7} impossible by
 * construction.
 */
public record RiskScore(
        int probability,
        int impact,
        int score
) {

    public RiskScore {
        if (probability < 1 || probability > 5) {
            throw new IllegalArgumentException(
                    "Probability must be between 1 and 5"
            );
        }

        if (impact < 1 || impact > 5) {
            throw new IllegalArgumentException(
                    "Impact must be between 1 and 5"
            );
        }

        if (score != probability * impact) {
            throw new IllegalArgumentException(
                    "Risk score must equal probability × impact"
            );
        }
    }

    public static RiskScore of(Probability probability, ImpactLevel impact) {
        int p = probability.score();
        int i = impact.score();

        return new RiskScore(p, i, p * i);
    }

    public boolean isCritical() {
        return score >= 20;
    }

    public boolean isHigh() {
        return score >= 12;
    }

    public boolean isMedium() {
        return score >= 6;
    }

    public boolean isLow() {
        return score < 6;
    }
}
