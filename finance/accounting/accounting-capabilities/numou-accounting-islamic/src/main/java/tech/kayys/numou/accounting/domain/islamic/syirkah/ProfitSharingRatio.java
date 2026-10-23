package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact profit-sharing ratio stored as basis points (100.00% = 10,000). */
public record ProfitSharingRatio(int basisPoints) {
    public static final int TOTAL_BASIS_POINTS = 10_000;

    public ProfitSharingRatio {
        if (basisPoints < 0 || basisPoints > TOTAL_BASIS_POINTS) {
            throw new IllegalArgumentException("basisPoints must be between 0 and 10000");
        }
    }

    public static ProfitSharingRatio ofPercent(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "percentage must not be null");
        if (percentage.signum() < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
        int basisPoints;
        try {
            basisPoints = percentage.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("percentage precision cannot exceed 2 decimal places", ex);
        }
        return new ProfitSharingRatio(basisPoints);
    }

    public BigDecimal percentage() {
        return BigDecimal.valueOf(basisPoints, 2);
    }
}
