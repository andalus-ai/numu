package tech.kayys.syirkah.commerce.promotion.domain;

import java.util.Objects;

/**
 * A promotion candidate discovered by the resolver (product04.md §4).
 *
 * <p>The resolver only answers "which promotions could possibly matter?" —
 * it does not decide whether a promotion actually applies. That remains the
 * evaluation engine's job. The candidate carries the versioned identity so
 * the bulk load and evaluation bind to the exact promotion version.</p>
 */
public record PromotionCandidate(
        PromotionId promotionId,
        PromotionVersionId versionId
) {

    public PromotionCandidate {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(versionId, "versionId cannot be null");
    }
}
