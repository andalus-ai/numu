package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.target.LineTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;

/** Resolves the evaluation context's current line (product03.md). */
public record CompiledCurrentLineTarget() implements CompiledTarget {

    @Override
    public PromotionTargetSelection resolve(PromotionEvaluationContext context) {
        var lineId = context.currentLineId().orElseThrow(
                () -> new IllegalStateException(
                        "current_line target requires currentLineId"));
        return new SingleTargetSelection(new LineTarget(lineId.value()));
    }
}
