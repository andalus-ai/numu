package tech.kayys.numou.commerce.promotion.domain.target;

import java.util.List;

/** Immutable resolution of one or more targets for a compiled promotion effect. */
public interface PromotionTargetSelection {
    List<PromotionTarget> targets();

    /** Returns the first resolved target; throws when the selection is empty. */
    default PromotionTarget primary() {
        List<PromotionTarget> resolved = targets();
        if (resolved.isEmpty()) {
            throw new IllegalStateException("Promotion target selection is empty");
        }
        return resolved.getFirst();
    }
}
