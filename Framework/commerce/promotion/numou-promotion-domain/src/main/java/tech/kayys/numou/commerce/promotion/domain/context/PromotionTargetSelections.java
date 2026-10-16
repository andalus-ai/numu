package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.commerce.promotion.domain.target.LineTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.MultiTargetSelection;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.util.List;
import java.util.Objects;

/** Helpers for resolving {@link PromotionTargetSelection} from evaluation context. */
public final class PromotionTargetSelections {

    private PromotionTargetSelections() {}

    public static PromotionTargetSelection linesMatchingSku(
            PromotionEvaluationContext context,
            SkuId skuId
    ) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(skuId, "skuId cannot be null");

        List<PromotionTarget> matches = context.lines().stream()
                .filter(line -> line.skuId().map(skuId::equals).orElse(false))
                .map(line -> (PromotionTarget) new LineTarget(line.id().value()))
                .toList();

        if (matches.size() == 1) {
            return new SingleTargetSelection(matches.getFirst());
        }
        return new MultiTargetSelection(matches);
    }
}
