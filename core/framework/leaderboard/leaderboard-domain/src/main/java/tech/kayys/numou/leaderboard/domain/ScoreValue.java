package tech.kayys.numou.leaderboard.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact, currency-free score quantity. Values are bounded to six fractional digits. */
public record ScoreValue(BigDecimal amount) implements Comparable<ScoreValue> {
    public static final int MAX_SCALE = 6;
    public static final int MAX_PRECISION = 18;

    public ScoreValue {
        Objects.requireNonNull(amount, "amount");
        try {
            amount = amount.setScale(MAX_SCALE, RoundingMode.UNNECESSARY).stripTrailingZeros();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("score supports at most six fractional digits", ex);
        }
        if (amount.precision() > MAX_PRECISION
                || amount.abs().compareTo(BigDecimal.TEN.pow(12)) >= 0) {
            throw new IllegalArgumentException("score exceeds NUMERIC(18,6) storage range");
        }
        if (amount.signum() == 0) amount = BigDecimal.ZERO;
    }

    public static ScoreValue of(String amount) {
        return new ScoreValue(new BigDecimal(Objects.requireNonNull(amount, "amount")));
    }

    public static ScoreValue of(long amount) {
        return new ScoreValue(BigDecimal.valueOf(amount));
    }

    public ScoreValue plus(ScoreValue other) {
        return new ScoreValue(amount.add(Objects.requireNonNull(other, "other").amount));
    }

    public ScoreValue negate() {
        return new ScoreValue(amount.negate());
    }

    @Override
    public int compareTo(ScoreValue other) {
        return amount.compareTo(Objects.requireNonNull(other, "other").amount);
    }
}