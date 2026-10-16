package tech.kayys.syirkah.commerce.promotion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.action.BundlePrice;
import tech.kayys.syirkah.commerce.promotion.domain.action.FixedAmountOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.FreeUnits;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.AllLinesPresent;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumCartTotal;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PromotionEngine (blueprint Phase F scenarios)")
class PromotionEngineTest {

    private static final Instant AT = Instant.parse("2026-06-15T10:00:00Z");
    private static final DateRange VALID =
            new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    private static final DateRange EXPIRED =
            new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

    private final PromotionEngine engine = new PromotionEngine();

    private static Money idr(long amount) {
        return Money.of(amount, "IDR");
    }

    private static PromotionContext cart(PromotionLine... lines) {
        return new PromotionContext(List.of(lines), null, "POS", AT);
    }

    private static Promotion active(
            String name, int priority, DateRange validFor, PromotionRule rule) {
        return Promotion.draft(PromotionId.generate(), name, priority, validFor, rule)
                .activate();
    }

    private static void assertMoney(long expected, Money actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual.amount()),
                () -> "expected " + expected + " but was " + actual.amount());
    }

    @Test
    void buysTwoCoffeeGetTenPercentOff() {
        var coffee = new PromotionLine("COFFEE", 2, idr(18000));
        var cart = cart(coffee);
        var promo = active("BUY2-10", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new PercentOff(Percentage.of(10))));

        var outcome = engine.apply(List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertMoney(3600, outcome.get().discount());
        assertMoney(32400, outcome.get().discountedPrice().finalPrice());
        assertEquals(1, outcome.get().discountedPrice().adjustments().size());
        assertTrue(outcome.get().discountedPrice().adjustments().get(0).amount().isNegative());
    }

    @Test
    void coffeePlusCroissantBundlePrice() {
        var cart = cart(
                new PromotionLine("COFFEE", 1, idr(25000)),
                new PromotionLine("CROISSANT", 1, idr(18000)));
        var promo = active("BUNDLE-40K", 1, VALID, new PromotionRule(
                new AllLinesPresent(List.of("COFFEE", "CROISSANT")),
                new BundlePrice(idr(40000))));

        var outcome = engine.apply(List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertMoney(3000, outcome.get().discount());
        assertMoney(40000, outcome.get().discountedPrice().finalPrice());
    }

    @Test
    void buyFiveGetOneFree() {
        var cart = cart(new PromotionLine("COFFEE", 5, idr(5000)));
        var promo = active("BUY5-GET1", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 5),
                new FreeUnits("COFFEE", 1)));

        var outcome = engine.apply(List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertMoney(5000, outcome.get().discount());
        assertMoney(20000, outcome.get().discountedPrice().finalPrice());
    }

    @Test
    void annualPlanTwentyPercentOff() {
        var cart = cart(new PromotionLine("SAAS-ANNUAL", 1, idr(100000)));
        var promo = active("ANNUAL-20", 1, VALID, new PromotionRule(
                new MinimumCartTotal(idr(1)),
                new PercentOff(Percentage.of(20))));

        var outcome = engine.apply(List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertMoney(20000, outcome.get().discount());
        assertMoney(80000, outcome.get().discountedPrice().finalPrice());
    }

    @Test
    void higherDiscountWins() {
        var cart = cart(new PromotionLine("COFFEE", 4, idr(10000)));
        var small = active("SMALL", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(idr(5000))));
        var big = active("BIG", 5, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(idr(9000))));

        var outcome = engine.apply(List.of(small, big), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertEquals("BIG", outcome.get().promotion().name());
        assertMoney(9000, outcome.get().discount());
    }

    @Test
    void equalDiscountPrefersLowerPriorityThenName() {
        var cart = cart(new PromotionLine("COFFEE", 4, idr(10000)));
        var a = active("ALPHA", 2, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(idr(5000))));
        var b = active("BRAVO", 2, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(idr(5000))));
        var c = active("ZULU", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(idr(5000))));

        var outcome = engine.apply(List.of(b, a, c), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertEquals("ZULU", outcome.get().promotion().name());
    }

    @Test
    void draftExpiredOrUnmatchedPromotionsDoNotApply() {
        var cart = cart(new PromotionLine("COFFEE", 1, idr(10000)));
        var draft = Promotion.draft(PromotionId.generate(), "DRAFT", 1, VALID,
                new PromotionRule(
                        new MinimumQuantityOf("COFFEE", 1),
                        new PercentOff(Percentage.of(10))));
        var expired = active("EXPIRED", 1, EXPIRED, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 1),
                new PercentOff(Percentage.of(10))));
        var unmatched = active("UNMATCHED", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 5),
                new PercentOff(Percentage.of(10))));

        var outcome = engine.apply(
                List.of(draft, expired, unmatched),
                cart, PriceResult.flat(cart.total()));

        assertFalse(outcome.isPresent());
    }

    @Test
    void rejectsCartPriceThatDisagreesWithLines() {
        var cart = cart(new PromotionLine("COFFEE", 1, idr(10000)));

        assertThrows(IllegalArgumentException.class, () -> engine.apply(
                List.of(), cart, PriceResult.flat(idr(99999))));
    }

    @Test
    void discountNeverExceedsCartTotal() {
        var cart = cart(new PromotionLine("COFFEE", 1, idr(3000)));
        var promo = active("TOO-BIG", 1, VALID, new PromotionRule(
                new MinimumQuantityOf("COFFEE", 1),
                new FixedAmountOff(idr(50000))));

        var outcome = engine.apply(List.of(promo), cart, PriceResult.flat(cart.total()));

        assertTrue(outcome.isPresent());
        assertMoney(3000, outcome.get().discount());
        assertTrue(outcome.get().discountedPrice().finalPrice().isZero());
    }
}
