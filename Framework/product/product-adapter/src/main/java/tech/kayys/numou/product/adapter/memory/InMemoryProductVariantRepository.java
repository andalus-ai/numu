package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;
import tech.kayys.syirkah.product.spi.port.ProductVariantRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProductVariantRepository} adapter. */
public final class InMemoryProductVariantRepository
        implements ProductVariantRepository {

    private final Map<ProductVariantId, ProductVariant> variantsById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProductVariant> save(ProductVariant variant) {
        Objects.requireNonNull(variant, "variant cannot be null");

        variantsById.put(variant.id(), variant);

        return CompletableFuture.completedFuture(variant);
    }

    @Override
    public CompletionStage<Optional<ProductVariant>> findById(
            ProductVariantId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(variantsById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ProductVariant>> findByProductId(
            ProductId productId
    ) {
        Objects.requireNonNull(productId, "productId cannot be null");

        var found = variantsById.values().stream()
                .filter(variant ->
                        variant.productId().equals(productId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Boolean> existsById(ProductVariantId id) {
        return CompletableFuture.completedFuture(
                variantsById.containsKey(id)
        );
    }

    @Override
    public CompletionStage<Void> delete(ProductVariant variant) {
        Objects.requireNonNull(variant, "variant cannot be null");

        variantsById.remove(variant.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProductVariantId id) {
        variantsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}