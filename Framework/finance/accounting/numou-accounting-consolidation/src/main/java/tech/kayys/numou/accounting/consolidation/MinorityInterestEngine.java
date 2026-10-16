package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculates Non-Controlling Interest (NCI) shares of subsidiary net income and equity.
 */
public final class MinorityInterestEngine {

    public record NciAttribution(
            BigDecimal parentShare,
            BigDecimal nciShare
    ) {}

    /**
     * Attributes net profit or equity between Parent and NCI according to effective ownership.
     *
     * @param totalAmount subsidiary net income or net assets
     * @param parentOwnership parent effective ownership percentage (e.g. 0.80)
     * @return NciAttribution with parentShare and nciShare
     */
    public NciAttribution attribute(BigDecimal totalAmount, BigDecimal parentOwnership) {
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(parentOwnership, "parentOwnership must not be null");

        BigDecimal parentShare = totalAmount.multiply(parentOwnership).setScale(4, RoundingMode.HALF_UP);
        BigDecimal nciShare = totalAmount.subtract(parentShare);
        return new NciAttribution(parentShare, nciShare);
    }
}
