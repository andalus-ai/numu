package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.context.CartSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.DefaultPromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineId;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.LineTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.ProductOfferingTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.ProductTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.SkuTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Target compilation (product02.md §15-§18): a configuration reference resolves
 * to a concrete {@code PromotionTargetSelection} at evaluation time.
 */
@DisplayName("Target compiler registry")
class TargetCompilerRegistryTest {

    private final DefaultTargetCompilerRegistry registry =
            DefaultTargetCompilerRegistry.defaults();

    private static DefaultPromotionEvaluationContext context() {
        Money price = Money.of(10000, "IDR");
        return new DefaultPromotionEvaluationContext(
                Instant.parse("2026-06-15T10:00:00Z"),
                ChannelId.of("POS"),
                Optional.empty(),
                new CartSnapshot(price, Money.zero(price.currency()), price),
                List.of(new PromotionLineSnapshot(
                        PromotionLineId.of("COFFEE"),
                        Optional.empty(),
                        Optional.empty(),
                        "COFFEE",
                        Quantity.of(3, Unit.of("pcs")),
                        price)),
                Optional.of(PromotionLineId.of("COFFEE")));
    }

    @Test
    void compilesCartTarget() {
        var selection = registry.require(TargetType.of("cart"))
                .compile(new TargetDefinition(TargetType.of("cart"), Map.of()))
                .resolve(context());

        assertEquals(CartTarget.INSTANCE, selection.primary());
    }

    @Test
    void compilesCurrentLineTarget() {
        var selection = registry.require(TargetType.of("current_line"))
                .compile(new TargetDefinition(TargetType.of("current_line"), Map.of()))
                .resolve(context());

        assertEquals(new LineTarget("COFFEE"), selection.primary());
    }

    @Test
    void compilesProductSkuAndOfferingTargets() {
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        var product = registry.require(TargetType.of("product"))
                .compile(new TargetDefinition(TargetType.of("product"),
                        Map.of("productId", productId.toString())))
                .resolve(context());
        var sku = registry.require(TargetType.of("sku"))
                .compile(new TargetDefinition(TargetType.of("sku"),
                        Map.of("skuId", skuId.toString())))
                .resolve(context());
        var offering = registry.require(TargetType.of("offering"))
                .compile(new TargetDefinition(TargetType.of("offering"),
                        Map.of("offeringId", offeringId.toString())))
                .resolve(context());

        assertTrue(product.primary() instanceof ProductTarget);
        assertTrue(sku.primary() instanceof SkuTarget);
        assertTrue(offering.primary() instanceof ProductOfferingTarget);
        assertEquals(skuId, ((SkuTarget) sku.primary()).skuId().value());
        assertEquals(offeringId,
                ((ProductOfferingTarget) offering.primary()).offeringId().value());
    }

    @Test
    void rejectsIdentityTargetsWithoutAReference() {
        assertThrows(PromotionCompilationException.class,
                () -> registry.require(TargetType.of("sku"))
                        .compile(new TargetDefinition(TargetType.of("sku"), Map.of())));
    }

    @Test
    void rejectsUnknownTargetType() {
        assertThrows(PromotionCompilationException.class,
                () -> registry.require(TargetType.of("no_such_target")));
    }
}