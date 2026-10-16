package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.context.CategoryId;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CustomerId;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.commerce.promotion.domain.context.SkuId;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;

import java.util.List;
import java.util.Set;

/**
 * Explicit scope for a promotion (product04.md section 8).
 *
 * <p>Empty sets have defined semantics: an empty set means "all" for that
 * dimension, never "none". The resolver uses this to narrow candidates
 * without putting business conditions into SQL.</p>
 */
public record PromotionScope(
        Set<ChannelId> channels,
        Set<tech.kayys.syirkah.commerce.promotion.domain.context.BranchId> branches,
        Set<ProductId> products,
        Set<SkuId> skus,
        Set<CategoryId> categories,
        Set<String> customerSegments
) {

    public PromotionScope {
        channels = Set.copyOf(channels);
        branches = Set.copyOf(branches);
        products = Set.copyOf(products);
        skus = Set.copyOf(skus);
        categories = Set.copyOf(categories);
        customerSegments = Set.copyOf(customerSegments);
    }

    public static PromotionScope all() {
        return new PromotionScope(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static PromotionScope of(
            Set<ChannelId> channels,
            Set<tech.kayys.syirkah.commerce.promotion.domain.context.BranchId> branches,
            Set<ProductId> products,
            Set<SkuId> skus,
            Set<CategoryId> categories,
            Set<String> customerSegments
    ) {
        return new PromotionScope(channels, branches, products, skus, categories, customerSegments);
    }
}
