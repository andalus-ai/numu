package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import java.util.Objects;

/**
 * Stacking configuration carried by a promotion.
 *
 * {@code priority} is the stacking/tie-break priority (lower wins).
 * {@code stackingGroup} optionally partitions exclusivity
 * (promotions in different groups may coexist under EXCLUSIVE).
 */
public record PromotionStackingConfiguration(
        PromotionStackingMode mode,
        int priority,
        String stackingGroup
) {

    public static final String DEFAULT_GROUP = "DEFAULT";

    public PromotionStackingConfiguration {
        Objects.requireNonNull(mode, "mode cannot be null");
        if (stackingGroup == null || stackingGroup.isBlank()) {
            stackingGroup = DEFAULT_GROUP;
        } else {
            stackingGroup = stackingGroup.trim();
        }
    }

    public static PromotionStackingConfiguration bestResult(int priority) {
        return new PromotionStackingConfiguration(
                PromotionStackingMode.BEST_RESULT, priority, DEFAULT_GROUP);
    }

    public static PromotionStackingConfiguration stack(int priority) {
        return new PromotionStackingConfiguration(
                PromotionStackingMode.STACK, priority, DEFAULT_GROUP);
    }

    public static PromotionStackingConfiguration exclusive(
            int priority, String stackingGroup) {
        return new PromotionStackingConfiguration(
                PromotionStackingMode.EXCLUSIVE, priority, stackingGroup);
    }
}
