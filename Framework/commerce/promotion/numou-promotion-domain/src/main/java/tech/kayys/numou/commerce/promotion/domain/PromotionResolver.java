package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.List;

/**
 * Promotion candidate resolver (product04.md section 5).
 *
 * <p>The resolver answers only "which promotions could possibly matter?" It
 * performs candidate reduction using hard filters (tenant, status, effective
 * period, channel, branch, coupon) and soft/reference filters (product, SKU,
 * category, customer segment). It does NOT decide whether a promotion
 * actually applies -- that remains the evaluation engine's job in P6.</p>
 *
 * <p>The resolver returns versioned {@link PromotionCandidate} ids, NOT full
 * aggregates. Bulk loading the definitions is a separate step behind the
 * {@link PromotionRepository} port, so the resolver stays storage-agnostic
 * (product04.md section 28).</p>
 */
public interface PromotionResolver {

    /**
     * Resolves the candidate promotions for the given request.
     *
     * @param request the narrow lookup request built from the transaction
     *               context
     * @return the candidate version ids, deduplicated and never null
     */
    List<PromotionCandidate> resolve(PromotionResolveRequest request);

    /**
     * Convenience overload that builds a {@link PromotionResolveRequest} from
     * the controlled evaluation context.
     */
    default List<PromotionCandidate> resolve(PromotionEvaluationContext context) {
        return resolve(PromotionResolveRequestFactory.from(context));
    }
}
