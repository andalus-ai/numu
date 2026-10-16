package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.spi.port.ClassificationNodeRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ClassificationNodeRepository} adapter. */
public final class InMemoryClassificationNodeRepository
        implements ClassificationNodeRepository {

    private final Map<ClassificationNodeId, ClassificationNode> nodesById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ClassificationNode> save(ClassificationNode node) {
        Objects.requireNonNull(node, "node cannot be null");

        nodesById.put(node.id(), node);

        return CompletableFuture.completedFuture(node);
    }

    @Override
    public CompletionStage<Optional<ClassificationNode>> findById(
            ClassificationNodeId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(nodesById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsByCode(
            ClassificationSchemeId schemeId,
            String code
    ) {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");

        return CompletableFuture.completedFuture(
                nodesById.values().stream()
                        .anyMatch(node ->
                                node.schemeId().equals(schemeId)
                                        && node.code().equals(code))
        );
    }

    @Override
    public CompletionStage<List<ClassificationNode>> childrenOf(
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentNodeId
    ) {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");

        return CompletableFuture.completedFuture(
                nodesById.values().stream()
                        .filter(node -> node.schemeId().equals(schemeId))
                        .filter(node ->
                                Objects.equals(node.parentNodeId(), parentNodeId))
                        .sorted(Comparator.comparingInt(
                                ClassificationNode::sortOrder))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ClassificationNodeId id) {
        return CompletableFuture.completedFuture(nodesById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ClassificationNode node) {
        Objects.requireNonNull(node, "node cannot be null");

        nodesById.remove(node.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ClassificationNodeId id) {
        nodesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
