package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionEngine;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionLine;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.FixedAmountOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Promotion stacking & composition (product03)")
class PromotionStackingPolicyTest {

    private static final Instant AT = Instant.parse("2026-06-15T10:00:00Z");
    private static final DateRange VALID =
            new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    private final PromotionEngine engine = new PromotionEngine();

    private static Money idr(long amount) {
        return Money.of(amount, "IDR");
    }

    private static PromotionContext cart() {
        return new PromotionContext(
                List.of(new PromotionLine("COFFEE", 4, idr(10000))),
                null,
                "POS",
                AT);
    }

    private static Promotion promo(
            String name,
            int priority,
            long discount,
            PromotionStackingConfiguration stacking
    ) {
        return Promotion.draft(
                        PromotionId.generate(),
                        name,
                        priority,
                        VALID,
                        new PromotionRule(
                                new MinimumQuantityOf("COFFEE", 2),
                                new FixedAmountOff(idr(discount))),
                        stacking)
                .activate();
    }

    @Test
    void bestResultKeepsSingleWinnerAndReportsRejected() {
        var cart = cart();
        var small = promo("SMALL", 1, 5000,
                PromotionStackingConfiguration.bestResult(1));
        var big = promo("BIG", 5, 9000,
                PromotionStackingConfiguration.bestResult(5));

        var result = engine.evaluate(
                List.of(small, big), cart, PriceResult.flat(cart.total()));

        assertEquals(1, result.applied().size());
        assertEquals("BIG", result.applied().getFirst().promotionCode());
        assertEquals(1, result.rejected().size());
        assertEquals(
                PromotionRejectionReason.LOST_BEST_RESULT,
                result.rejected().getFirst().reason().code());
        assertEquals(0, BigDecimal.valueOf(9000)
                .compareTo(result.price().totalDiscount().amount()));
    }

    @Test
    void stackModeAppliesCompatiblePromotionsTogether() {
        var cart = cart();
        var a = promo("STACK-A", 1, 3000,
                PromotionStackingConfiguration.stack(1));
        var b = promo("STACK-B", 2, 2000,
                PromotionStackingConfiguration.stack(2));

        var result = engine.evaluate(
                List.of(a, b), cart, PriceResult.flat(cart.total()));

        assertEquals(2, result.applied().size());
        assertTrue(result.rejected().isEmpty());
        assertEquals(0, BigDecimal.valueOf(5000)
                .compareTo(result.price().totalDiscount().amount()));
    }

    @Test
    void exclusivePromotionExcludesOthersInSameGroup() {
        var cart = cart();
        var flash = promo("FLASH", 1, 8000,
                PromotionStackingConfiguration.exclusive(1, "CAMPAIGN"));
        var regular = promo("REGULAR", 2, 5000,
                new PromotionStackingConfiguration(
                        PromotionStackingMode.STACK, 2, "CAMPAIGN"));

        var result = engine.evaluate(
                List.of(flash, regular), cart, PriceResult.flat(cart.total()));

        assertEquals(1, result.applied().size());
        assertEquals("FLASH", result.applied().getFirst().promotionCode());
        assertEquals(1, result.rejected().size());
        assertEquals(
                PromotionRejectionReason.EXCLUDED_BY_EXCLUSIVE,
                result.rejected().getFirst().reason().code());
    }

    @Test
    void percentOffPreservesMechanismUntilComposition() {
        var coffee = new PromotionLine("COFFEE", 2, idr(18000));
        var cart = new PromotionContext(List.of(coffee), null, "POS", AT);
        var promo = Promotion.draft(
                        PromotionId.generate(),
                        "BUY2-10",
                        1,
                        VALID,
                        new PromotionRule(
                                new MinimumQuantityOf("COFFEE", 2),
                                new PercentOff(Percentage.of(10))))
                .activate();

        var result = engine.evaluate(
                List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(result.hasApplications());
        assertTrue(result.applied().getFirst().benefits().getFirst()
                instanceof tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit);
        assertEquals(0, BigDecimal.valueOf(3600)
                .compareTo(result.price().totalDiscount().amount()));
    }
}
