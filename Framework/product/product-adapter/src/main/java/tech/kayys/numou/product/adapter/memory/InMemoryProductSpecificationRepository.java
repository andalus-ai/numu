package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProductSpecificationRepository} adapter. */
public final class InMemoryProductSpecificationRepository
        implements ProductSpecificationRepository {

    private final Map<ProductSpecificationId, ProductSpecification>
            specificationsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProductSpecification> save(
            ProductSpecification specification
    ) {
        Objects.requireNonNull(
                specification,
                "specification cannot be null"
        );

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
        Objects.requireNonNull(productId, "productId cannot be null");

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
        Objects.requireNonNull(
                specification,
                "specification cannot be null"
        );

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