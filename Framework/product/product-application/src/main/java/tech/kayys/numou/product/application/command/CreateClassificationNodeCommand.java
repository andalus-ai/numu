package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;

import java.util.Objects;

/**
 * Creates a classification node under a scheme. {@code parentNodeId}
 * may be null to create a root node.
 */
public record CreateClassificationNodeCommand(
        ClassificationSchemeId schemeId,
        ClassificationNodeId parentNodeId,
        String code,
        String name,
        int sortOrder
) implements Command {

    public CreateClassificationNodeCommand {
        Objects.requireNonNull(schemeId, "schemeId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}
