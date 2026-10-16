package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.spi.port.SkuRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link SkuRepository}. */
public final class InMemorySkuRepository implements SkuRepository {

    private final Map<SkuId, Sku> skusById = new LinkedHashMap<>();

    @Override
    public CompletionStage<Sku> save(Sku sku) {
        skusById.put(sku.id(), sku);

        return CompletableFuture.completedFuture(sku);
    }

    @Override
    public CompletionStage<Optional<Sku>> findById(SkuId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(skusById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<Sku>> findByCode(String code) {
        return CompletableFuture.completedFuture(
                skusById.values().stream()
                        .filter(sku -> sku.code().equals(code))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
        return CompletableFuture.completedFuture(
                skusById.values().stream()
                        .anyMatch(sku -> sku.code().equals(code))
        );
    }

    @Override
    public CompletionStage<List<Sku>> findByProductId(
            ProductId productId
    ) {
        var found = skusById.values().stream()
                .filter(sku -> sku.productId().equals(productId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Boolean> existsById(SkuId id) {
        return CompletableFuture.completedFuture(skusById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Sku sku) {
        skusById.remove(sku.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(SkuId id) {
        skusById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}