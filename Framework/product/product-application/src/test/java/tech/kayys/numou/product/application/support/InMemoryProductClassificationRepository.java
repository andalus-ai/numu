package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.classification.ProductClassification;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductClassificationRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProductClassificationRepository}. */
public final class InMemoryProductClassificationRepository
        implements ProductClassificationRepository {

    private final Set<ProductClassification> classifications =
            new LinkedHashSet<>();

    @Override
    public CompletionStage<Void> save(ProductClassification classification) {
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
        return CompletableFuture.completedFuture(
                classifications.stream()
                        .filter(c -> c.nodeId().equals(nodeId))
                        .toList()
        );
    }
}
