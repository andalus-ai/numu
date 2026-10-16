package tech.kayys.syirkah.commerce.promotion.adapter.memory;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionCandidate;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionResolveRequest;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionStatus;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionVersionId;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.TenantRef;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionCandidatePort;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory promotion store for tests, demos and single-process
 * deployments. Implements both candidate discovery and definition
 * persistence (product04.md section 5).
 *
 * <p>Candidate discovery returns versioned {@link PromotionCandidate} ids;
 * bulk loading the definitions is a separate step behind the
 * {@link PromotionRepository} port, so discovery stays storage-agnostic
 * (product04.md section 28).</p>
 */
public final class InMemoryPromotionRepository
        implements PromotionCandidatePort, PromotionRepository {

    private final Map<PromotionId, Promotion> promotions = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Promotion> save(Promotion promotion) {
        promotions.put(promotion.id(), promotion);
        return CompletableFuture.completedFuture(promotion);
    }

    @Override
    public CompletionStage<Optional<Promotion>> findById(PromotionId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(promotions.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsByName(String name) {
        String normalized = name == null ? "" : name.trim();
        boolean exists = promotions.values().stream()
                .anyMatch(promotion -> promotion.name().equalsIgnoreCase(normalized));
        return CompletableFuture.completedFuture(exists);
    }

    @Override
    public CompletionStage<List<Promotion>> findActiveOn(LocalDate date) {
        return CompletableFuture.completedFuture(
                promotions.values().stream()
                        .filter(promotion -> promotion.isCandidateFor(date))
                        .toList());
    }

    @Override
    public CompletionStage<List<PromotionCandidate>> findCandidates(
            PromotionResolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        LocalDate effectiveAt = request.effectiveAt()
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        List<PromotionCandidate> candidates = promotions.values().stream()
                .filter(promotion -> promotion.tenantId().equals(request.tenantId()))
                .filter(promotion -> promotion.status() == PromotionStatus.ACTIVE)
                .filter(promotion -> promotion.validFor().contains(effectiveAt))
                .filter(promotion -> matchesChannel(promotion, request.channel()))
                .filter(promotion -> matchesBranch(promotion, request.branchId()))
                .filter(promotion -> matchesCoupon(promotion, request.couponCode()))
                .map(p -> new PromotionCandidate(
                        p.id(), PromotionVersionId.of(p.id().value())))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(candidates);
    }

    @Override
    public CompletionStage<Map<PromotionVersionId, Promotion>> loadAll(
            List<PromotionVersionId> versionIds) {
        Map<PromotionVersionId, Promotion> loaded = new java.util.HashMap<>();
        for (var versionId : versionIds) {
            var promotion = promotions.get(versionId.value());
            if (promotion != null) {
                loaded.put(versionId, promotion);
            }
        }
        return CompletableFuture.completedFuture(loaded);
    }

    private static boolean matchesChannel(
            Promotion promotion, ChannelId requestChannel) {
        if (promotion.scope().channels().isEmpty()) {
            return true;
        }
        return promotion.scope().channels().contains(requestChannel);
    }

    private static boolean matchesBranch(
            Promotion promotion,
            java.util.Optional<tech.kayys.syirkah.commerce.promotion.domain.context.BranchId> requestBranch) {
        if (promotion.scope().branches().isEmpty()) {
            return true;
        }
        return requestBranch.map(promotion.scope().branches()::contains).orElse(false);
    }

    private static boolean matchesCoupon(
            Promotion promotion,
            java.util.Optional<String> requestCoupon) {
        if (requestCoupon.isEmpty()) {
            return true;
        }
        return promotion.name().equalsIgnoreCase(requestCoupon.get());
    }
}
