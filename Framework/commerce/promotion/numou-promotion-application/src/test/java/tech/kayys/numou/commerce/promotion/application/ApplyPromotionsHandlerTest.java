package tech.kayys.syirkah.commerce.promotion.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.application.handler.ApplyPromotionsHandler;
import tech.kayys.syirkah.commerce.promotion.application.query.ApplyPromotionsQuery;
import tech.kayys.syirkah.commerce.promotion.application.support.StubPromotionCandidates;
import tech.kayys.syirkah.commerce.promotion.domain.DefaultPromotionResolver;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApplyPromotionsHandler")
class ApplyPromotionsHandlerTest {

    private final StubPromotionCandidates repository = new StubPromotionCandidates();
    private final ApplyPromotionsHandler handler = new ApplyPromotionsHandler(
            new DefaultPromotionResolver(repository), repository);

    private static PromotionContext cart() {
        return new PromotionContext(
                List.of(new tech.kayys.syirkah.commerce.promotion.domain.PromotionLine(
                        "COFFEE", 2, Money.of(18000, "IDR"))),
                null,
                "POS",
                Instant.parse("2026-06-15T10:00:00Z"));
    }

    @Test
    void appliesBestActivePromotion() {
        repository.add(Promotion.draft(
                        PromotionId.generate(), "BUY2-10", 1,
                        new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                        new PromotionRule(
                                new MinimumQuantityOf("COFFEE", 2),
                                new PercentOff(Percentage.of(10))),
                        PromotionStackingConfiguration.bestResult(1),
                        TenantRef.of("DEFAULT"),
                        tech.kayys.syirkah.commerce.promotion.domain.PromotionScope.all())
                .activate());

        var result = handler.handle(new ApplyPromotionsQuery(cart()))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.orElseThrow().isPresent());
        var outcome = result.orElseThrow().get();
        assertEquals(0, new BigDecimal("3600").compareTo(outcome.discount().amount()));
        assertEquals(0, new BigDecimal("32400")
                .compareTo(outcome.discountedPrice().finalPrice().amount()));
    }

    @Test
    void emptyCandidateListIsSuccessWithNoOutcome() {
        var result = handler.handle(new ApplyPromotionsQuery(cart()))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertFalse(result.orElseThrow().isPresent());
    }
}
