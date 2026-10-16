package tech.kayys.syirkah.commerce.pricing.adapter.memory;

import tech.kayys.syirkah.commerce.pricing.domain.price.PriceList;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.commerce.pricing.spi.port.PriceListRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory price-list store. */
public final class InMemoryPriceListRepository implements PriceListRepository {

    private final Map<PriceListId, PriceList> byId = new ConcurrentHashMap<>();
    private final Map<String, PriceListId> byCode = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<PriceList> save(PriceList aggregate) {
        byId.put(aggregate.id(), aggregate);
        byCode.put(aggregate.code(), aggregate.id());
        return CompletableFuture.completedFuture(aggregate);
    }

    @Override
    public CompletionStage<Optional<PriceList>> findById(PriceListId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(byId.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(PriceListId id) {
        return CompletableFuture.completedFuture(byId.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(PriceList aggregate) {
        return deleteById(aggregate.id());
    }

    @Override
    public CompletionStage<Void> deleteById(PriceListId id) {
        var removed = byId.remove(id);
        if (removed != null) {
            byCode.remove(removed.code(), id);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<PriceList>> findByCode(String code) {
        var id = byCode.get(normalize(code));
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(byId.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
        return CompletableFuture.completedFuture(byCode.containsKey(normalize(code)));
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
