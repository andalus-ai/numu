package tech.kayys.numou.commerce.promotion.domain.target;

import java.util.Objects;

/** Identifies one cart line using the stable line key from the evaluation snapshot. */
public record LineTarget(String lineKey) implements PromotionTarget {
    public LineTarget {
        Objects.requireNonNull(lineKey, "lineKey cannot be null");
        lineKey = lineKey.trim();
        if (lineKey.isEmpty()) {
            throw new IllegalArgumentException("lineKey cannot be blank");
        }
    }
}
