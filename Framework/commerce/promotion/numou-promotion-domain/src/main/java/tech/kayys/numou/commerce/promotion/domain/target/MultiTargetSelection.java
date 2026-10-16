package tech.kayys.numou.commerce.promotion.domain.target;

import java.util.List;
import java.util.Objects;

/** Selection containing zero or more targets, in deterministic evaluation order. */
public record MultiTargetSelection(List<PromotionTarget> targets) implements PromotionTargetSelection {
    public MultiTargetSelection {
        Objects.requireNonNull(targets, "targets cannot be null");
        targets = List.copyOf(targets);
        if (targets.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("targets cannot contain null");
        }
    }
}
