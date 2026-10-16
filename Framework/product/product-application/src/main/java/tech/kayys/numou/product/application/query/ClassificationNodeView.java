package tech.kayys.syirkah.product.application.query;

import tech.kayys.syirkah.product.domain.classification.ClassificationNode;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeStatus;

import java.util.List;
import java.util.Objects;

/** Read model for one classification node (product02.md). */
public record ClassificationNodeView(
        ClassificationNodeId id,
        String code,
        String name,
        int sortOrder,
        ClassificationNodeStatus status,
        List<ClassificationNodeView> children
) {

    public ClassificationNodeView {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(children, "children cannot be null");
        children = List.copyOf(children);
    }

    public static ClassificationNodeView from(ClassificationNode node) {
        return from(node, List.of());
    }

    public static ClassificationNodeView from(
            ClassificationNode node,
            List<ClassificationNodeView> children
    ) {
        return new ClassificationNodeView(
                node.id(),
                node.code(),
                node.name(),
                node.sortOrder(),
                node.status(),
                children);
    }
}
