package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PromotionCompiler")
class PromotionCompilerTest {

    private final PromotionCompiler compiler = new PromotionCompiler();

    @Test
    void compilesDraftPromotionWithFingerprint() {
        var promotion = Promotion.draft(
                PromotionId.generate(),
                "BUY2-10",
                1,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                new PromotionRule(
                        new MinimumQuantityOf("COFFEE", 2),
                        new PercentOff(Percentage.of(10))));

        var result = compiler.compile(promotion);

        assertTrue(result.isSuccess());
        assertFalse(result.compiled().orElseThrow().fingerprint().isBlank());
    }

    @Test
    void rejectsInactivePromotion() {
        var promotion = Promotion.draft(
                        PromotionId.generate(),
                        "OLD",
                        1,
                        new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                        new PromotionRule(
                                new MinimumQuantityOf("COFFEE", 2),
                                new PercentOff(Percentage.of(10))))
                .deactivate();

        var result = compiler.compile(promotion);

        assertFalse(result.isSuccess());
    }
}
