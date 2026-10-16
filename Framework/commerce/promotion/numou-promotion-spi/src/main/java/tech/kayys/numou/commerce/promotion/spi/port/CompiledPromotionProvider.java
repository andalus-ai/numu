package tech.kayys.syirkah.commerce.promotion.spi.port;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Cache/provider for compiled promotions used by the runtime engine
 * (product03.md CompiledPromotionProvider).
 */
public interface CompiledPromotionProvider {

    CompletionStage<Optional<CompiledPromotion>> get(PromotionId id);

    CompletionStage<List<CompiledPromotion>> getAll(Collection<PromotionId> ids);

    CompletionStage<Void> put(CompiledPromotion compiled);

    CompletionStage<Void> invalidate(PromotionId id);
}
