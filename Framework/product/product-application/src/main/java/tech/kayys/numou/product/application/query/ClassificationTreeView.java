package tech.kayys.syirkah.product.application.query;

import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.List;
import java.util.Objects;

/** Full classification tree for a scheme (product02.md). */
public record ClassificationTreeView(
        ClassificationSchemeId schemeId,
        List<ClassificationNodeView> roots
) {

    public ClassificationTreeView {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(roots, "roots cannot be null");
        roots = List.copyOf(roots);
    }
}
