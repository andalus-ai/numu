package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;

import java.util.Objects;

/** Archives an active classification node. */
public record ArchiveClassificationNodeCommand(
        ClassificationNodeId nodeId
) implements Command {

    public ArchiveClassificationNodeCommand {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
