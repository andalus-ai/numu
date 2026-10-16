package tech.kayys.syirkah.product.spi.port;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for classification nodes.
 *
 * {@link #childrenOf} supports both the tree read model and the
 * ancestor walk the application layer uses to reject moves that would
 * create a cycle - the node aggregate itself only guards against
 * becoming its own parent (product02.md, section 5).
 */
public interface ClassificationNodeRepository
        extends Repository<ClassificationNode, ClassificationNodeId> {

    CompletionStage<Boolean> existsByCode(
            ClassificationSchemeId schemeId,
            String code
    );

    /**
     * Direct children of {@code parentNodeId} within a scheme, ordered
     * by sort order. A null {@code parentNodeId} returns the roots.
     */
    CompletionStage<List<ClassificationNode>> childrenOf(
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentNodeId
    );
}
