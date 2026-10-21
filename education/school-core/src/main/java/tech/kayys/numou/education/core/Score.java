package tech.kayys.numou.education.core;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A 0-100 score kept at two decimal places. */
public record Score(BigDecimal value) implements Comparable<Score>, Serializable {
    public Score {
        Objects.requireNonNull(value, "value must not be null");
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Score must be between 0 and 100: " + value);
        }
    }

    public static Score of(double v) { return new Score(BigDecimal.valueOf(v)); }
    public static Score of(String v) { return new Score(new BigDecimal(v)); }

    public Predikat predikat(ScoreBands bands) {
        if (value.compareTo(bands.minA()) >= 0) return Predikat.A;
        if (value.compareTo(bands.minB()) >= 0) return Predikat.B;
        if (value.compareTo(bands.minC()) >= 0) return Predikat.C;
        return Predikat.D;
    }

    public boolean meets(Score kkm) { return compareTo(kkm) >= 0; }

    @Override
    public int compareTo(Score o) { return value.compareTo(o.value); }

    @Override
    public String toString() { return value.toPlainString(); }
}
