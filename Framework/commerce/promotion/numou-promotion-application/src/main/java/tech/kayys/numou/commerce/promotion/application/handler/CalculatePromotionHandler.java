package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.promotion.application.query.CalculatePromotionQuery;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionEngine;
import tech.kayys.syirkah.commerce.promotion.domain.result.PromotionResult;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionCandidatePort;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Full promotion calculation returning applied/rejected promotions
 * (product03.md CalculatePromotion).
 */
public final class CalculatePromotionHandler
        implements QueryHandler<CalculatePromotionQuery, Result<PromotionResult>> {

    private final PromotionCandidatePort candidates;
    private final PromotionEngine engine;

    public CalculatePromotionHandler(PromotionCandidatePort candidates) {
        this(candidates, new PromotionEngine());
    }

    public CalculatePromotionHandler(
            PromotionCandidatePort candidates,
            PromotionEngine engine
    ) {
        this.candidates = Objects.requireNonNull(candidates);
        this.engine = Objects.requireNonNull(engine);
    }

    @Override
    public Uni<Result<PromotionResult>> handle(CalculatePromotionQuery query) {
        PromotionContext cart = query.cart();
        PriceResult cartPrice = PriceResult.flat(cart.total());
        return Uni.createFrom()
                .completionStage(candidates.findActiveOn(
                        cart.at().atZone(ZoneOffset.UTC).toLocalDate()))
                .map(active -> Result.success(
                        engine.evaluate(active, cart, cartPrice)));
    }
}
