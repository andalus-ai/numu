package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.classification.ProductClassification;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link ProductClassification} association.
 *
 * Not a {@code Repository<A, ID>}: a product classification is a value
 * association, not an aggregate root, so it has no identity or lifecycle
 * of its own. The application layer coordinates the cross-aggregate rule
 * {@code classification.schemeId == node.schemeId} around this port.
 */
public interface ProductClassificationRepository {

    CompletionStage<Void> save(ProductClassification classification);

    CompletionStage<Void> remove(
            ProductId productId,
            ClassificationSchemeId schemeId,
            ClassificationNodeId nodeId
    );

    CompletionStage<Boolean> exists(
            ProductId productId,
            ClassificationSchemeId schemeId,
            ClassificationNodeId nodeId
    );

    CompletionStage<List<ProductClassification>> findByProductId(
            ProductId productId
    );

    CompletionStage<List<ProductClassification>> findByNode(
            ClassificationNodeId nodeId
    );
}
