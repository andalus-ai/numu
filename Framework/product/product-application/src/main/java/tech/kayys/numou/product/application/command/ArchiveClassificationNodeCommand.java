package tech.kayys.numou.product.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.product.domain.classification.ClassificationNodeId;

import java.util.Objects;

/** Archives an active classification node. */
public record ArchiveClassificationNodeCommand(
        ClassificationNodeId nodeId
) implements Command {

    public ArchiveClassificationNodeCommand {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
