package tech.kayys.syirkah.commerce.promotion.adapter.memory;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("InMemoryPromotionRepository")
class InMemoryPromotionRepositoryTest {

    private static Promotion promotion(String name, DateRange validFor) {
        return Promotion.draft(
                PromotionId.generate(), name, 1, validFor,
                new PromotionRule(
                        new MinimumQuantityOf("COFFEE", 2),
                        new PercentOff(Percentage.of(10))));
    }

    @Test
    void returnsOnlyActivePromotionsOnDate() {
        var repository = new InMemoryPromotionRepository();
        var active = promotion("ACTIVE", new DateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        var draft = promotion("DRAFT", new DateRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        repository.save(active.activate());
        repository.save(draft);

        var result = repository.findActiveOn(LocalDate.of(2026, 6, 15))
                .toCompletableFuture().join();

        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).name());
    }

    @Test
    void excludesPromotionsOutsideValidityWindow() {
        var repository = new InMemoryPromotionRepository();
        repository.save(promotion("EXPIRED", new DateRange(
                        LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .activate());

        var result = repository.findActiveOn(LocalDate.of(2026, 6, 15))
                .toCompletableFuture().join();

        assertEquals(0, result.size());
    }
}
