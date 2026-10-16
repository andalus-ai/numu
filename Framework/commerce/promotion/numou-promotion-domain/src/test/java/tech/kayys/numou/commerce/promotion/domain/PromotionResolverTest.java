package tech.kayys.syirkah.commerce.promotion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.context.CartSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.DefaultPromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineId;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PromotionResolver (P5)")
class PromotionResolverTest {

    private static final Instant AT = Instant.parse("2026-06-15T10:00:00Z");
    private static final LocalDate TODAY = AT.atZone(ZoneOffset.UTC).toLocalDate();

    private static Promotion tenantPromotion(TenantRef tenant, String channel) {
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
                PromotionScope.of(
                        Set.of(ChannelId.of(channel)),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of()))
                .activate();
    }

    @Test
    void resolvesTenantScopedActivePromotion() {
        var tenant = TenantRef.of("TENANT-A");
        var other = TenantRef.of("TENANT-B");
        var repo = new InMemoryRepo();
        repo.save(tenantPromotion(tenant, "POS"));
        repo.save(tenantPromotion(other, "POS"));

        var resolver = new DefaultPromotionResolver(repo);
        var request = new PromotionResolveRequest(
                tenant, AT, ChannelId.of("POS"), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Set.of(), Set.of(), Set.of(),
                Optional.empty());

        var candidates = resolver.resolve(request);

        assertEquals(1, candidates.size());
        assertEquals(tenant, repo.load(candidates.get(0)).tenantId());
    }

    @Test
    void returnsEmptyWhenChannelDoesNotMatch() {
        var tenant = TenantRef.of("TENANT-A");
        var repo = new InMemoryRepo();
        repo.save(tenantPromotion(tenant, "POS"));

        var resolver = new DefaultPromotionResolver(repo);
        var request = new PromotionResolveRequest(
                tenant, AT, ChannelId.of("ONLINE"), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Set.of(), Set.of(), Set.of(),
                Optional.empty());

        var candidates = resolver.resolve(request);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void resolvesFromEvaluationContext() {
        var tenant = TenantRef.of("TENANT-A");
        var repo = new InMemoryRepo();
        repo.save(tenantPromotion(tenant, "POS"));

        var context = new DefaultPromotionEvaluationContext(
                AT, ChannelId.of("POS"), Optional.empty(),
                new CartSnapshot(Money.of(20000, "IDR"), Money.zero("IDR"),
                        Money.of(20000, "IDR")),
                List.of(new PromotionLineSnapshot(
                        PromotionLineId.of("COFFEE"),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        "COFFEE",
                        Quantity.of(2, Unit.of("pcs")),
                        Money.of(10000, "IDR"))),
                Optional.empty(),
                tenant,
                Optional.empty());

        var resolver = new DefaultPromotionResolver(repo);
        var candidates = resolver.resolve(context);

        assertEquals(1, candidates.size());
    }

    private static final class InMemoryRepo implements PromotionCandidateRepository {
        private final Map<PromotionId, Promotion> store = new ConcurrentHashMap<>();

        void save(Promotion promotion) {
            store.put(promotion.id(), promotion);
        }

        Promotion load(PromotionCandidate candidate) {
            return store.get(candidate.promotionId());
        }

        @Override
        public List<PromotionCandidate> findCandidates(PromotionResolveRequest request) {
            LocalDate effectiveAt = request.effectiveAt()
                    .atZone(ZoneOffset.UTC).toLocalDate();
            return store.values().stream()
                    .filter(p -> p.tenantId().equals(request.tenantId()))
                    .filter(p -> p.status() == PromotionStatus.ACTIVE)
                    .filter(p -> p.validFor().contains(effectiveAt))
                    .filter(p -> p.scope().channels().isEmpty()
                            || p.scope().channels().contains(request.channel()))
                    .map(p -> new PromotionCandidate(
                            p.id(), PromotionVersionId.of(p.id().value())))
                    .toList();
        }
    }
}
