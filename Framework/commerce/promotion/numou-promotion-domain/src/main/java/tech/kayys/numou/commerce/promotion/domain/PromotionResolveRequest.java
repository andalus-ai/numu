package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.context.BranchId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CategoryId;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CustomerId;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.commerce.promotion.domain.context.SkuId;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lookup request for the promotion resolver (product04.md section 3).
 *
 * <p>The resolver only needs the narrow set of references that can narrow
 * candidate retrieval: tenant, channel, branch, customer, time, product/SKU/
 * category references and an optional coupon code. It deliberately does NOT
 * receive the cart -- subtotal, quantity, shipping and line price belong to
 * eligibility evaluation, not candidate discovery.</p>
 */
public record PromotionResolveRequest(
        TenantRef tenantId,
        Instant effectiveAt,
        ChannelId channel,
        Optional<BranchId> branchId,
        Optional<CustomerId> customerId,
        Optional<String> customerSegment,
        Set<ProductId> productIds,
        Set<SkuId> skuIds,
        Set<CategoryId> categoryIds,
        Optional<String> couponCode
) {

    public PromotionResolveRequest {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("effectiveAt cannot be null");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel cannot be null");
        }
        productIds = Set.copyOf(productIds);
        skuIds = Set.copyOf(skuIds);
        categoryIds = Set.copyOf(categoryIds);
    }

    public static PromotionResolveRequest of(
            TenantRef tenantId,
            Instant effectiveAt,
            ChannelId channel,
            Optional<BranchId> branchId,
            Optional<CustomerId> customerId,
            Optional<String> customerSegment,
            List<ProductId> productIds,
            List<SkuId> skuIds,
            List<CategoryId> categoryIds,
            Optional<String> couponCode
    ) {
        return new PromotionResolveRequest(
                tenantId,
                effectiveAt,
                channel,
                branchId,
                customerId,
                customerSegment,
                productIds == null ? Set.of() : Set.copyOf(productIds),
                skuIds == null ? Set.of() : Set.copyOf(skuIds),
                categoryIds == null ? Set.of() : Set.copyOf(categoryIds),
                couponCode);
    }
}
