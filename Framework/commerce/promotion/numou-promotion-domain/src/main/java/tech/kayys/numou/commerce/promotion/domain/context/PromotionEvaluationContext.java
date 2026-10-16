package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;
import tech.kayys.syirkah.product.domain.sku.SkuId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Controlled snapshot boundary for promotion evaluation (product03.md).
 * Callers build this; promotion never navigates foreign aggregates.
 */
public interface PromotionEvaluationContext {

    Instant effectiveAt();

    ChannelId channel();

    Optional<CustomerSnapshot> customer();

    CartSnapshot cart();

    List<PromotionLineSnapshot> lines();

    Optional<PromotionLineId> currentLineId();

    /** Tenant identity (product04.md section 21). */
    TenantRef tenantId();

    /** Optional branch identity (product04.md section 21). */
    Optional<BranchId> branchId();

    /** Lines whose snapshot {@code skuId} equals {@code skuId}. */
    default PromotionTargetSelection linesMatchingSku(SkuId skuId) {
        return PromotionTargetSelections.linesMatchingSku(this, skuId);
    }
}
