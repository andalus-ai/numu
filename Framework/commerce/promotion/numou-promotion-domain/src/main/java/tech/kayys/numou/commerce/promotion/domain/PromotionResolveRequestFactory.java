package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.context.BranchId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CategoryId;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CustomerId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CustomerSnapshot;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.SkuId;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a {@link PromotionResolveRequest} from a controlled evaluation
 * context (product04.md section 3).
 *
 * <p>The resolver only needs the narrow set of references that can narrow
 * candidate retrieval. Cart-level business conditions (subtotal, quantity,
 * shipping, line price) are deliberately excluded -- they belong to eligibility
 * evaluation, not candidate discovery.</p>
 */
public final class PromotionResolveRequestFactory {

    private PromotionResolveRequestFactory() {
    }

    public static PromotionResolveRequest from(PromotionEvaluationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        Optional<BranchId> branchId = context.branchId();
        Optional<CustomerId> customerId = context.customer()
                .map(c -> CustomerId.of(c.id().value()));
        Optional<String> customerSegment = context.customer()
                .flatMap(CustomerSnapshot::segment)
                .map(String::trim)
                .filter(s -> !s.isEmpty());

        Set<ProductId> productIds = context.lines().stream()
                .map(line -> line.productId())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(pid -> ProductId.of(pid.value()))
                .collect(Collectors.toSet());
        Set<SkuId> skuIds = context.lines().stream()
                .map(line -> line.skuId())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(sku -> SkuId.of(sku.value()))
                .collect(Collectors.toSet());
        Set<CategoryId> categoryIds = context.lines().stream()
                .map(line -> line.categoryId())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(cat -> CategoryId.of(cat.value()))
                .collect(Collectors.toSet());

        return new PromotionResolveRequest(
                TenantRef.of(context.tenantId().value()),
                context.effectiveAt(),
                ChannelId.of(context.channel().value()),
                branchId,
                customerId,
                customerSegment,
                productIds,
                skuIds,
                categoryIds,
                Optional.empty());
    }
}
