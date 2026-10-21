package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demand/supply surge for one zone. {@code ratio = recent requests / available drivers}; the multiplier is the highest
 * tier whose threshold the ratio reaches, capped. Two guards keep it from feeling arbitrary:
 * a minimum demand (three requests is noise, not a rush) and a per-tick step limit so the price never lurches.
 */
public record SurgePolicy(List<Tier> tiers, int minDemand, BigDecimal maxStepUp, BigDecimal maxStepDown) {

    public record Tier(BigDecimal minRatio, BigDecimal multiplier) { }

    public static SurgePolicy defaults() {
        return new SurgePolicy(List.of(
                new Tier(new BigDecimal("1.5"), new BigDecimal("1.2")),
                new Tier(new BigDecimal("2.0"), new BigDecimal("1.5")),
                new Tier(new BigDecimal("3.0"), new BigDecimal("2.0")),
                new Tier(new BigDecimal("4.0"), new BigDecimal("2.5"))),
                5, new BigDecimal("0.3"), new BigDecimal("0.5"));
    }

    /** The multiplier the market calls for right now. */
    public BigDecimal target(int demand, int supply) {
        if (demand < minDemand) {
            return BigDecimal.ONE.setScale(1);
        }
        final BigDecimal ratio = BigDecimal.valueOf(demand).divide(BigDecimal.valueOf(Math.max(1, supply)), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal result = BigDecimal.ONE.setScale(1);
        for (final Tier t : tiers) {
            if (ratio.compareTo(t.minRatio()) >= 0 && t.multiplier().compareTo(result) > 0) {
                result = t.multiplier();
            }
        }
        return result;
    }

    /** Moves from the multiplier currently in force toward the target by at most one step. */
    public BigDecimal next(BigDecimal current, BigDecimal target) {
        if (target.compareTo(current) > 0) {
            return current.add(maxStepUp).min(target);
        }
        return current.subtract(maxStepDown).max(target).max(BigDecimal.ONE.setScale(1));
    }
}
