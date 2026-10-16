package tech.kayys.numou.commerce.promotion.domain.target;

import java.util.List;
import java.util.Objects;

/** Selection containing exactly one target. */
public record SingleTargetSelection(PromotionTarget target) implements PromotionTargetSelection {
    public SingleTargetSelection {
        Objects.requireNonNull(target, "target cannot be null");
    }

    @Override
    public List<PromotionTarget> targets() {
        return List.of(target);
    }
}
