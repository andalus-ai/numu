package tech.kayys.numou.commerce.promotion.application.support;

import tech.kayys.numou.commerce.promotion.domain.Promotion;
import tech.kayys.numou.commerce.promotion.domain.PromotionCandidate;
import tech.kayys.numou.commerce.promotion.domain.PromotionId;
import tech.kayys.numou.commerce.promotion.domain.PromotionResolveRequest;
import tech.kayys.numou.commerce.promotion.domain.PromotionVersionId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for the promotion candidate lookup.
 */
public final class StubPromotionCandidates
        implements tech.kayys.numou.commerce.promotion.domain.PromotionCandidateRepository,
                   tech.kayys.numou.commerce.promotion.spi.port.PromotionRepository {

    private final List<Promotion> promotions = new ArrayList<>();

    public void add(Promotion promotion) {
        promotions.add(promotion);
    }

    @Override
    public List<PromotionCandidate> findCandidates(PromotionResolveRequest request) {
        return promotions.stream()
                .map(p -> new PromotionCandidate(p.id(), PromotionVersionId.of(p.id().value())))
                .toList();
    }

    @Override
    public CompletionStage<Promotion> save(Promotion promotion) {
        promotions.add(promotion);
        return CompletableFuture.completedFuture(promotion);
    }

    @Override
    public CompletionStage<java.util.Optional<Promotion>> findById(PromotionId id) {
        return CompletableFuture.completedFuture(
                promotions.stream().filter(p -> p.id().equals(id)).findFirst());
    }

    @Override
    public CompletionStage<Boolean> existsByName(String name) {
        String normalized = name == null ? "" : name.trim();
        boolean exists = promotions.stream()
                .anyMatch(p -> p.name().equalsIgnoreCase(normalized));
        return CompletableFuture.completedFuture(exists);
    }

    @Override
    public CompletionStage<Map<PromotionVersionId, Promotion>> loadAll(
            List<PromotionVersionId> versionIds) {
        return CompletableFuture.completedFuture(
                promotions.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                p -> PromotionVersionId.of(p.id().value()),
                                p -> p)));
    }
}
