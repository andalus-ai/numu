package tech.kayys.numou.syirkah.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic exact apportionment (largest remainder method).
 *
 * <p>The returned parts always sum to exactly {@code total} at the requested scale;
 * ties are broken by key order, so the result never depends on map iteration order.
 * This replaces the "remainder goes to the last partner" shortcut, which biases results.</p>
 */
public final class Apportionment {

    private Apportionment() {
    }

    public static <K extends Comparable<K>> Map<K, BigDecimal> apportion(
            BigDecimal total, Map<K, BigDecimal> weights, int scale) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(weights, "weights");
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("scale must be >= 0");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal w : weights.values()) {
            if (w == null || w.signum() < 0) {
                throw new IllegalArgumentException("weights must be non-negative");
            }
            sum = sum.add(w);
        }
        if (sum.signum() == 0) {
            throw new IllegalArgumentException("weights must not all be zero");
        }
        final int sign = total.signum();
        final BigDecimal abs = total.abs().setScale(scale, RoundingMode.HALF_EVEN);
        if (abs.compareTo(total.abs()) != 0) {
            throw new IllegalArgumentException("total has more fraction digits than scale " + scale + ": " + total);
        }

        TreeMap<K, BigDecimal> sorted = new TreeMap<>(weights);
        Map<K, BigDecimal> floors = new LinkedHashMap<>();
        Map<K, BigDecimal> remainders = new LinkedHashMap<>();
        BigDecimal floorSum = BigDecimal.ZERO;
        for (Map.Entry<K, BigDecimal> e : sorted.entrySet()) {
            BigDecimal exact = abs.multiply(e.getValue()).divide(sum, scale + 20, RoundingMode.DOWN);
            BigDecimal floor = exact.setScale(scale, RoundingMode.DOWN);
            floors.put(e.getKey(), floor);
            remainders.put(e.getKey(), exact.subtract(floor));
            floorSum = floorSum.add(floor);
        }
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        int leftover = abs.subtract(floorSum).divide(unit, 0, RoundingMode.UNNECESSARY).intValueExact();

        List<K> order = new ArrayList<>(sorted.keySet());
        order.sort(Comparator.<K, BigDecimal>comparing(remainders::get).reversed().thenComparing(Comparator.naturalOrder()));
        for (int i = 0; i < leftover; i++) {
            K k = order.get(i % order.size());
            floors.put(k, floors.get(k).add(unit));
        }
        Map<K, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<K, BigDecimal> e : floors.entrySet()) {
            result.put(e.getKey(), sign < 0 ? e.getValue().negate() : e.getValue());
        }
        return result;
    }
}
