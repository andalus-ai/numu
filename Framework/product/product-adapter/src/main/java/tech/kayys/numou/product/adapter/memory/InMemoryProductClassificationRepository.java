package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.classification.ProductClassification;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductClassificationRepository;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ProductClassificationRepository} adapter. Backed by a
 * concurrent set since {@link ProductClassification} is a value record
 * with structural equality.
 */
public final class InMemoryProductClassificationRepository
        implements ProductClassificationRepository {

    private final Set<ProductClassification> classifications =
            ConcurrentHashMap.newKeySet();

    @Override
    public CompletionStage<Void> save(ProductClassification classification) {
        Objects.requireNonNull(classification, "classification cannot be null");

        classifications.add(classification);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> remove(
            ProductId productId,
            ClassificationSchemeId schemeId,
            ClassificationNodeId nodeId
    ) {
        classifications.remove(
                new ProductClassification(productId, schemeId, nodeId)
        );

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Boolean> exists(
            ProductId productId,
            ClassificationSchemeId schemeId,
            ClassificationNodeId nodeId
    ) {
        return CompletableFuture.completedFuture(
                classifications.contains(
                        new ProductClassification(productId, schemeId, nodeId))
        );
    }

    @Override
    public CompletionStage<List<ProductClassification>> findByProductId(
            ProductId productId
    ) {
        Objects.requireNonNull(productId, "productId cannot be null");

        return CompletableFuture.completedFuture(
                classifications.stream()
                        .filter(c -> c.productId().equals(productId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<ProductClassification>> findByNode(
            ClassificationNodeId nodeId
    ) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");

        return CompletableFuture.completedFuture(
                classifications.stream()
                        .filter(c -> c.nodeId().equals(nodeId))
                        .toList()
        );
    }
}
