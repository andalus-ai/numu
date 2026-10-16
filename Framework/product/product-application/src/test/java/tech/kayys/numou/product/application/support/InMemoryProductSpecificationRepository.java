package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProductSpecificationRepository}. */
public final class InMemoryProductSpecificationRepository
        implements ProductSpecificationRepository {

    private final Map<ProductSpecificationId, ProductSpecification>
            specificationsById = new LinkedHashMap<>();

    @Override
    public CompletionStage<ProductSpecification> save(
            ProductSpecification specification
    ) {
        specificationsById.put(specification.id(), specification);

        return CompletableFuture.completedFuture(specification);
    }

    @Override
    public CompletionStage<Optional<ProductSpecification>> findById(
            ProductSpecificationId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(specificationsById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ProductSpecification>> findByProductId(
            ProductId productId
    ) {
        var found = specificationsById.values().stream()
                .filter(specification ->
                        specification.productId().equals(productId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Boolean> existsById(
            ProductSpecificationId id
    ) {
        return CompletableFuture.completedFuture(
                specificationsById.containsKey(id)
        );
    }

    @Override
    public CompletionStage<Void> delete(
            ProductSpecification specification
    ) {
        specificationsById.remove(specification.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(
            ProductSpecificationId id
    ) {
        specificationsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}