package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates direct, indirect, and effective ownership percentages and Non-Controlling Interest (NCI).
 */
public final class OwnershipEngine {

    /**
     * Calculates effective ownership of an ultimate parent in a subsidiary through intermediate holding companies.
     *
     * @param directPercentages map of holding chain (e.g. Parent->HoldCo: 0.80, HoldCo->Sub: 0.75)
     * @return effective ownership product (e.g. 0.60)
     */
    public BigDecimal calculateEffectiveOwnership(BigDecimal... directPercentages) {
        if (directPercentages == null || directPercentages.length == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal effective = BigDecimal.ONE;
        for (BigDecimal pct : directPercentages) {
            Objects.requireNonNull(pct, "Ownership percentage cannot be null");
            if (pct.signum() < 0 || pct.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Ownership percentage must be between 0 and 1: " + pct);
            }
            effective = effective.multiply(pct).setScale(6, RoundingMode.HALF_UP);
        }
        return effective;
    }

    /**
     * Calculates Non-Controlling Interest (NCI) percentage from effective controlling ownership.
     */
    public BigDecimal calculateNciPercentage(BigDecimal effectiveOwnership) {
        Objects.requireNonNull(effectiveOwnership, "effectiveOwnership must not be null");
        return BigDecimal.ONE.subtract(effectiveOwnership).setScale(6, RoundingMode.HALF_UP);
    }
}
