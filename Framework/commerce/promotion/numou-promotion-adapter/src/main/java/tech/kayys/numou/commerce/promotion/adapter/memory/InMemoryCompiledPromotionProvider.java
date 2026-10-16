package tech.kayys.syirkah.commerce.promotion.adapter.memory;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledPromotion;
import tech.kayys.syirkah.commerce.promotion.spi.port.CompiledPromotionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory compiled promotion cache. */
public final class InMemoryCompiledPromotionProvider
        implements CompiledPromotionProvider {

    private final Map<PromotionId, CompiledPromotion> cache = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Optional<CompiledPromotion>> get(PromotionId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(cache.get(id)));
    }

    @Override
    public CompletionStage<List<CompiledPromotion>> getAll(Collection<PromotionId> ids) {
        List<CompiledPromotion> found = new ArrayList<>();
        for (var id : ids) {
            var compiled = cache.get(id);
            if (compiled != null) {
                found.add(compiled);
            }
        }
        return CompletableFuture.completedFuture(List.copyOf(found));
    }

    @Override
    public CompletionStage<Void> put(CompiledPromotion compiled) {
        cache.put(compiled.source().id(), compiled);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> invalidate(PromotionId id) {
        cache.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
