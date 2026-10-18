package tech.kayys.numou.syirkah.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Nisbah: each partner's fraction of net profit. Fractions are exact decimals,
 * every share is in (0,1], and the shares sum to exactly 1 (compared numerically,
 * never by BigDecimal.equals which is scale-sensitive).
 */
public final class ProfitSharingRatio implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TreeMap<PartnerId, BigDecimal> shares;

    private ProfitSharingRatio(TreeMap<PartnerId, BigDecimal> shares) {
        this.shares = shares;
    }

    /** Builds from fractions summing to 1, e.g. {A=0.4, B=0.6}. */
    public static ProfitSharingRatio ofFractions(Map<PartnerId, BigDecimal> fractions) {
        Objects.requireNonNull(fractions, "fractions");
        if (fractions.isEmpty()) {
            throw new IllegalArgumentException("nisbah requires at least one partner");
        }
        TreeMap<PartnerId, BigDecimal> copy = new TreeMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<PartnerId, BigDecimal> e : fractions.entrySet()) {
            BigDecimal v = Objects.requireNonNull(e.getValue(), "share");
            if (v.signum() <= 0 || v.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("each nisbah share must be in (0,1]: " + v);
            }
            copy.put(e.getKey(), v.stripTrailingZeros());
            sum = sum.add(v);
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("nisbah must sum to exactly 100%, was " + sum.movePointRight(2) + "%");
        }
        return new ProfitSharingRatio(copy);
    }

    /** Builds from percentages summing to 100, e.g. {A=40, B=60}. */
    public static ProfitSharingRatio ofPercent(Map<PartnerId, BigDecimal> percents) {
        TreeMap<PartnerId, BigDecimal> f = new TreeMap<>();
        percents.forEach((k, v) -> f.put(k, v.movePointLeft(2)));
        return ofFractions(f);
    }

    public Map<PartnerId, BigDecimal> shares() {
        return Collections.unmodifiableMap(shares);
    }

    public BigDecimal shareOf(PartnerId partner) {
        BigDecimal v = shares.get(partner);
        return v == null ? BigDecimal.ZERO : v;
    }

    public boolean includes(PartnerId partner) {
        return shares.containsKey(partner);
    }

    /** Canonical text used for hashing. */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        shares.forEach((k, v) -> sb.append(k.value()).append('=').append(v.toPlainString()).append(';'));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProfitSharingRatio r && canonical().equals(r.canonical());
    }

    @Override
    public int hashCode() {
        return canonical().hashCode();
    }

    @Override
    public String toString() {
        return "ProfitSharingRatio[" + canonical() + "]";
    }
}
