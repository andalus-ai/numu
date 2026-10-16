package tech.kayys.syirkah.product.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;

import java.util.Objects;

/**
 * Moves a classification node under a new parent. {@code newParentNodeId}
 * may be null to promote the node to a root. The handler rejects moves
 * that would create a cycle.
 */
public record MoveClassificationNodeCommand(
        ClassificationNodeId nodeId,
        ClassificationNodeId newParentNodeId
) implements Command {

    public MoveClassificationNodeCommand {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }
}
