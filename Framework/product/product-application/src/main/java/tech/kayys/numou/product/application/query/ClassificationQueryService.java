package tech.kayys.syirkah.product.application.query;

import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.spi.port.ClassificationNodeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Read-side classification tree queries (product02.md).
 */
public final class ClassificationQueryService {

    private final ClassificationNodeRepository nodes;

    public ClassificationQueryService(ClassificationNodeRepository nodes) {
        this.nodes = Objects.requireNonNull(nodes);
    }

    public CompletionStage<List<ClassificationNodeView>> childrenOf(
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentNodeId
    ) {
        return nodes.childrenOf(schemeId, parentNodeId)
                .thenApply(list -> list.stream()
                        .map(ClassificationNodeView::from)
                        .toList());
    }

    public CompletionStage<ClassificationTreeView> getTree(
            ClassificationSchemeId schemeId
    ) {
        return loadSubtree(schemeId, null)
                .thenApply(roots -> new ClassificationTreeView(schemeId, roots));
    }

    private CompletionStage<List<ClassificationNodeView>> loadSubtree(
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentId
    ) {
        return nodes.childrenOf(schemeId, parentId).thenCompose(children -> {
            List<CompletionStage<ClassificationNodeView>> stages = new ArrayList<>();
            for (ClassificationNode child : children) {
                stages.add(loadSubtree(schemeId, child.id())
                        .thenApply(grandchildren ->
                                ClassificationNodeView.from(child, grandchildren)));
            }
            if (stages.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            @SuppressWarnings("unchecked")
            CompletableFuture<ClassificationNodeView>[] array =
                    stages.stream()
                            .map(CompletionStage::toCompletableFuture)
                            .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(array)
                    .thenApply(ignored -> {
                        List<ClassificationNodeView> views = new ArrayList<>();
                        for (var future : array) {
                            views.add(future.join());
                        }
                        return List.copyOf(views);
                    });
        });
    }
}
