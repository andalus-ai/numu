package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;

import java.util.Objects;

/** A promotion rejected by the stacking policy. */
public record PromotionRejected(
        PromotionId promotionId,
        String promotionCode,
        PromotionRejectionReason reason
) {

    public PromotionRejected {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(promotionCode, "promotionCode cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
