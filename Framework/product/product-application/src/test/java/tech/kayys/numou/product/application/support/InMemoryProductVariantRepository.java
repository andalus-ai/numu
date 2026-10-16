package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;
import tech.kayys.syirkah.product.spi.port.ProductVariantRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProductVariantRepository}. */
public final class InMemoryProductVariantRepository
        implements ProductVariantRepository {

    private final Map<ProductVariantId, ProductVariant> variantsById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<ProductVariant> save(ProductVariant variant) {
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
        var found = variantsById.values().stream()
                .filter(variant -> variant.productId().equals(productId))
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
        variantsById.remove(variant.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProductVariantId id) {
        variantsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}