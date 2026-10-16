package tech.kayys.syirkah.commerce.promotion.domain.composition;

import java.util.Objects;

public record MonetaryCompositionPolicy(
        DiscountCompositionMode percentageMode
) {

    public MonetaryCompositionPolicy {
        Objects.requireNonNull(percentageMode, "percentageMode cannot be null");
    }

    public static MonetaryCompositionPolicy sequential() {
        return new MonetaryCompositionPolicy(DiscountCompositionMode.SEQUENTIAL);
    }

    public static MonetaryCompositionPolicy additive() {
        return new MonetaryCompositionPolicy(DiscountCompositionMode.ADDITIVE);
    }
}
