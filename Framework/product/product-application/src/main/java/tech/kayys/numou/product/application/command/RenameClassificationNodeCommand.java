package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;

import java.util.Objects;

/** Renames an active classification node. */
public record RenameClassificationNodeCommand(
        ClassificationNodeId nodeId,
        String name
) implements Command {

    public RenameClassificationNodeCommand {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}
