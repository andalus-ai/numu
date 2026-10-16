package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.promotion.application.query.ApplyPromotionsQuery;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionCandidate;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionEngine;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionOutcome;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionResolver;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContexts;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionResolveRequestFactory;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stateless promotion resolution (product04.md P5 + P6).
 *
 * <p>Orchestrates the clean P5 architecture from product04.md section 17:</p>
 * <pre>
 * EvaluationContext
 *   → PromotionResolver  (candidate version ids)
 *   → PromotionRepository.loadAll  (bulk load definitions)
 *   → PromotionEngine.evaluate  (P6 eligibility)
 * </pre>
 *
 * <p>Success with {@code Optional.empty()} is a normal outcome -- "no promotion
 * applies" is not an error.</p>
 */
public final class ApplyPromotionsHandler
        implements QueryHandler<ApplyPromotionsQuery, Result<Optional<PromotionOutcome>>> {

    private final PromotionResolver resolver;
    private final PromotionRepository repository;
    private final PromotionEngine engine;

    public ApplyPromotionsHandler(
            PromotionResolver resolver,
            PromotionRepository repository
    ) {
        this(resolver, repository, new PromotionEngine());
    }

    public ApplyPromotionsHandler(
            PromotionResolver resolver,
            PromotionRepository repository,
            PromotionEngine engine
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
    }

    @Override
    public Uni<Result<Optional<PromotionOutcome>>> handle(ApplyPromotionsQuery query) {
        var cart = query.cart();
        var cartPrice = cartPriceOf(cart);
        var context = PromotionEvaluationContexts.from(cart);
        var candidates = resolver.resolve(context);
        var versionIds = candidates.stream()
                .map(PromotionCandidate::versionId)
                .toList();
        return Uni.createFrom()
                .completionStage(repository.loadAll(versionIds))
                .map(loaded -> Result.success(
                        engine.apply(loaded.values().stream().toList(), cart, cartPrice)));
    }

    /**
     * The cart price is derived from the pre-priced lines so callers
     * cannot smuggle in a total that disagrees with the lines.
     */
    private static PriceResult cartPriceOf(PromotionContext cart) {
        return PriceResult.flat(cart.total());
    }
}
