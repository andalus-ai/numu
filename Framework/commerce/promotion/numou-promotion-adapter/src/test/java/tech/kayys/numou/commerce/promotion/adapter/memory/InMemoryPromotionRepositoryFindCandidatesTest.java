package tech.kayys.syirkah.commerce.promotion.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionCandidate;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionResolveRequest;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryPromotionRepository.findCandidates (typed path)")
class InMemoryPromotionRepositoryFindCandidatesTest {

    private static final Instant AT = Instant.parse("2026-06-15T10:00:00Z");
    private static final LocalDate TODAY = AT.atZone(java.time.ZoneOffset.UTC).toLocalDate();

    private static Promotion active(TenantRef tenant, String channel) {
        return Promotion.draft(
                PromotionId.generate(),
                "PROMO-10",
                1,
                new DateRange(TODAY, TODAY.plusDays(1)),
                new PromotionRule(
                        new MinimumQuantityOf("COFFEE", 2),
                        new PercentOff(Percentage.of(10))),
                PromotionStackingConfiguration.bestResult(1),
                tenant,
                tech.kayys.syirkah.commerce.promotion.domain.PromotionScope.of(
                        Set.of(ChannelId.of(channel)),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of()))
                .activate();
    }

    @Test
    void resolvesByTenantAndChannel() {
        var repository = new InMemoryPromotionRepository();
        repository.save(active(TenantRef.of("TENANT-A"), "POS"));
        repository.save(active(TenantRef.of("TENANT-B"), "POS"));

        var request = new PromotionResolveRequest(
                TenantRef.of("TENANT-A"), AT, ChannelId.of("POS"),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Set.of(), Set.of(), Set.of(), Optional.empty());

        var candidates = repository.findCandidates(request).toCompletableFuture().join();

        assertEquals(1, candidates.size());
        assertEquals(TenantRef.of("TENANT-A"),
                repository.findById(candidates.get(0).promotionId())
                        .toCompletableFuture().join().orElseThrow().tenantId());
    }

    @Test
    void returnsEmptyWhenChannelDoesNotMatch() {
        var repository = new InMemoryPromotionRepository();
        repository.save(active(TenantRef.of("TENANT-A"), "POS"));

        var request = new PromotionResolveRequest(
                TenantRef.of("TENANT-A"), AT, ChannelId.of("ONLINE"),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Set.of(), Set.of(), Set.of(), Optional.empty());

        var candidates = repository.findCandidates(request).toCompletableFuture().join();

        assertTrue(candidates.isEmpty());
    }
}
