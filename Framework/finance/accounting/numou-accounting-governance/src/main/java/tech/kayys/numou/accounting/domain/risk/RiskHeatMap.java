package tech.kayys.syirkah.accounting.domain.risk;

public final class RiskHeatMap {

    public enum HeatBand { LOW, MEDIUM, HIGH, CRITICAL }

    public record ScoreResult(int score, HeatBand band) {}

    public static ScoreResult evaluate(int likelihood, int impact) {
        int l = Math.max(1, Math.min(5, likelihood));
        int i = Math.max(1, Math.min(5, impact));
        int score = l * i;

        HeatBand band;
        if (score <= 5) {
            band = HeatBand.LOW;
        } else if (score <= 10) {
            band = HeatBand.MEDIUM;
        } else if (score <= 16) {
            band = HeatBand.HIGH;
        } else {
            band = HeatBand.CRITICAL;
        }
        return new ScoreResult(score, band);
    }
}
